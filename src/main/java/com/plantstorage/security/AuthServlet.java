package com.plantstorage.security;

import com.google.gson.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class AuthServlet extends HttpServlet {
    private final AuthSettings settings;
    private final Gson gson = new Gson();
    private final ArrayDeque<Long> attempts = new ArrayDeque<>();
    public AuthServlet(AuthSettings settings) { this.settings = settings; }
    // Single account: a process-wide limiter also works behind Render's proxy.
    // It does not trust client-supplied forwarding headers. Resets on a server restart.
    private synchronized boolean allowAttempt() {
        long now = System.currentTimeMillis();
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= 300_000) attempts.removeFirst();
        if (attempts.size() >= 10) return false;
        attempts.addLast(now); return true;
    }
    private void json(HttpServletResponse resp, Object body) throws IOException {
        resp.setContentType("application/json"); resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(body));
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/session".equals(req.getPathInfo())) { AuthFilter.error(resp, 404, "Not found"); return; }
        boolean authenticated = AuthFilter.loggedIn(req);
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(authenticated ? settings.idleSeconds() : 600);
        json(resp, Map.of("authenticated", authenticated, "csrfToken", AuthFilter.token(session)));
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/logout".equals(req.getPathInfo())) {
            HttpSession session = req.getSession(false);
            if (session != null) session.invalidate();
            Cookie cookie = new Cookie("PLANTSESSION", ""); cookie.setPath("/"); cookie.setHttpOnly(true);
            cookie.setSecure(settings.secureCookies()); cookie.setMaxAge(0); cookie.setAttribute("SameSite", "Strict"); resp.addCookie(cookie);
            json(resp, Map.of("status", "Signed out")); return;
        }
        if (!"/login".equals(req.getPathInfo())) { AuthFilter.error(resp, 404, "Not found"); return; }
        if (!allowAttempt()) { resp.setHeader("Retry-After", "300"); AuthFilter.error(resp, 429, "Too many sign-in attempts. Wait five minutes and try again."); return; }
        if (req.getContentType() == null || !req.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")) {
            AuthFilter.error(resp, 415, "Use a JSON request."); return;
        }
        byte[] bytes = req.getInputStream().readNBytes(4097);
        if (bytes.length > 4096) { AuthFilter.error(resp, 413, "Request too large."); return; }
        String username;
        char[] password;
        try {
            JsonObject body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            username = body.get("username").getAsString();
            password = body.get("password").getAsString().toCharArray();
            if (username.length() > 100 || password.length > 256) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { AuthFilter.error(resp, 400, "Enter a username and password."); return; }
        boolean valid;
        try {
            boolean passwordMatches = PasswordHash.verify(password, settings.passwordHash());
            boolean userMatches = MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8), settings.username().getBytes(StandardCharsets.UTF_8));
            valid = passwordMatches & userMatches;
        } finally { Arrays.fill(password, '\0'); Arrays.fill(bytes, (byte)0); }
        if (!valid) { AuthFilter.error(resp, 401, "Incorrect username or password."); return; }
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(settings.idleSeconds());
        session.setAttribute(AuthFilter.USER, true);
        session.setAttribute(AuthFilter.LOGIN_TIME, System.currentTimeMillis());
        json(resp, Map.of("authenticated", true, "csrfToken", AuthFilter.token(session)));
    }
}
