package com.plantstorage.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class AuthFilter implements Filter {
    static final String USER = "clientAuthenticated";
    static final String TOKEN = "csrfToken";
    static final String LOGIN_TIME = "loginTime";
    private static final Set<String> PUBLIC_GET = Set.of("/login.html", "/login.js", "/login.css", "/auth/session", "/healthz");
    private final AuthSettings settings;
    public AuthFilter(AuthSettings settings) { this.settings = settings; }
    public static boolean loggedIn(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(USER))) return false;
        Long since = (Long)session.getAttribute(LOGIN_TIME);
        if (since == null || System.currentTimeMillis() - since > 8L * 60 * 60 * 1000) {
            session.invalidate(); return false;
        }
        return true;
    }
    public static String token(HttpSession session) {
        synchronized (session) {
            String token = (String)session.getAttribute(TOKEN);
            if (token == null) {
                byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(TOKEN, token);
            }
            return token;
        }
    }
    public static void error(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code); resp.setContentType("application/json"); resp.setCharacterEncoding("UTF-8");
        // Callers supply fixed messages, never request content.
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse resp = (HttpServletResponse)response;
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        if (settings.secureCookies()) resp.setHeader("Strict-Transport-Security", "max-age=31536000");
        String path = req.getServletPath() + Objects.toString(req.getPathInfo(), "");
        String method = req.getMethod();
        boolean safe = method.equals("GET") || method.equals("HEAD");
        boolean authenticated = loggedIn(req);
        boolean login = path.equals("/auth/login") && method.equals("POST");
        if (!authenticated && !(safe && PUBLIC_GET.contains(path)) && !login) {
            if (safe && (path.equals("/") || path.equals("/index.html"))) {
                resp.setStatus(303); resp.setHeader("Location", "/login.html");
            } else error(resp, 401, "Please sign in.");
            return;
        }
        if (!safe) {
            HttpSession session = req.getSession(false);
            String expected = session == null ? null : (String)session.getAttribute(TOKEN);
            String supplied = req.getHeader("X-CSRF-Token");
            if (!settings.origin().equals(req.getHeader("Origin")) || expected == null || supplied == null ||
                !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                error(resp, 403, "Request could not be verified. Refresh the page and try again."); return;
            }
        }
        // Prevent a servlet (including the photo proxy) from caching private responses.
        HttpServletResponseWrapper privateResponse = new HttpServletResponseWrapper(resp) {
            @Override public void setHeader(String name, String value) { super.setHeader(name, name.equalsIgnoreCase("Cache-Control") ? "no-store" : value); }
            @Override public void addHeader(String name, String value) {
                if (name.equalsIgnoreCase("Cache-Control")) super.setHeader(name, "no-store");
                else super.addHeader(name, value);
            }
        };
        try { chain.doFilter(req, privateResponse); }
        catch (Exception ex) {
            req.getServletContext().log("Request failed: " + ex.getClass().getSimpleName());
            if (!resp.isCommitted()) { resp.resetBuffer(); error(resp, 500, "The request failed. Please try again."); }
        }
    }
}
