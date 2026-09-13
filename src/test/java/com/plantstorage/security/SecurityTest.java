package com.plantstorage.security;

import com.google.gson.JsonParser;
import java.net.*;
import java.net.http.*;
import java.util.*;

public final class SecurityTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    private static HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, String url, String token, String origin, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (token != null) request.header("X-CSRF-Token", token);
        if (origin != null) request.header("Origin", origin);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String token(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject().get("csrfToken").getAsString(); }
    public static void main(String[] args) throws Exception {
        String hash = PasswordHash.create("temporary-fixture-password".toCharArray());
        check(PasswordHash.verify("temporary-fixture-password".toCharArray(), hash), "Correct password");
        check(!PasswordHash.verify("wrong password".toCharArray(), hash), "Incorrect password");
        check(!hash.equals(PasswordHash.create("temporary-fixture-password".toCharArray())), "Random salts");
        try { new AuthSettings("user", hash, "http://public.example", 60); throw new AssertionError("Public HTTP allowed"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { new AuthSettings("user", "invalid", "https://private.example", 60); throw new AssertionError("Invalid hash allowed"); }
        catch (IllegalArgumentException expected) { checks++; }
        try (SecurityFixture fixture = new SecurityFixture(8091, null, hash)) {
            String base = fixture.origin;
            HttpClient client = client();
            for (String path : List.of("/plants/", "/photos/?plantId=1&id=1", "/identify/", "/script.js", "/auth/unknown"))
                check(get(client, base + path).statusCode() == 401, "Anonymous blocked: " + path);
            check(get(client, base + "/").statusCode() == 303, "Home redirects to login");
            check(get(client, base + "/login.html").statusCode() == 200, "Login page public");
            check(get(client, base + "/healthz").body().equals("ok"), "Minimal public health check");
            HttpResponse<String> session = get(client, base + "/auth/session");
            String csrf = token(session);
            String cookie = session.headers().firstValue("set-cookie").orElseThrow();
            check(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict"), "Session cookie flags");
            String oldCookie = cookie.split(";", 2)[0];
            String credentials = "{\"username\":\"test-client\",\"password\":\"temporary-fixture-password\"}";
            check(post(client, base + "/auth/login", null, base, credentials).statusCode() == 403, "Login needs CSRF");
            check(post(client, base + "/auth/login", csrf, "https://attacker.example", credentials).statusCode() == 403, "Cross-site login blocked");
            check(post(client, base + "/auth/login", csrf, base, "{\"username\":\"test-client\",\"password\":\"wrong\"}").statusCode() == 401, "Bad login rejected");
            HttpResponse<String> login = post(client, base + "/auth/login", csrf, base, credentials);
            check(login.statusCode() == 200, "Valid login");
            String loggedCookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
            check(!loggedCookie.equals(oldCookie), "Session ID rotated");
            String newToken = token(login);
            check(!newToken.equals(csrf), "CSRF token rotated");
            check(get(client, base + "/plants/").statusCode() == 200, "Authenticated data access");
            check(get(client, base + "/photos/").headers().firstValue("cache-control").orElse("").equals("no-store"), "Private photos not cached");
            check(get(client, base + "/").headers().firstValue("content-security-policy").orElse("").contains("frame-ancestors 'none'"), "CSP");
            check(post(client, base + "/plants/", csrf, base, "{}").statusCode() == 403, "Old CSRF rejected");
            check(post(client, base + "/plants/", newToken, "https://attacker.example", "{}").statusCode() == 403, "Cross-site mutation rejected");
            check(fixture.mutations.get() == 0, "Blocked changes have no side effects");
            check(post(client, base + "/plants/", newToken, base, "{}").statusCode() == 200, "Valid mutation");
            check(fixture.mutations.get() == 1, "Exactly one mutation");
            check(post(client, base + "/auth/logout", newToken, base, "{}").statusCode() == 200, "Logout");
            check(get(client, base + "/plants/").statusCode() == 401, "Logged out access denied");
            HttpResponse<String> replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/plants/")).header("Cookie", loggedCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(replay.statusCode() == 401, "Old logged-in cookie cannot be replayed after logout");
            csrf = token(get(client, base + "/auth/session"));
            check(post(client, base + "/auth/login", csrf, base, credentials).statusCode() == 200, "Relogin");
            get(client, base + "/plants/?expire=1");
            check(get(client, base + "/plants/").statusCode() == 401, "Absolute session expiry");
            csrf = token(get(client, base + "/auth/session"));
            int last = 0;
            for (int i = 0; i < 12; i++) last = post(client, base + "/auth/login", csrf, base, "{}").statusCode();
            check(last == 429, "Repeated attempts limited");
        }
        try (SecurityFixture fixture = new SecurityFixture(8092, "https://private.example", hash)) {
            HttpResponse<String> session = get(client(), fixture.origin + "/auth/session");
            check(session.headers().firstValue("set-cookie").orElse("").contains("Secure"), "Hosted cookie Secure even behind HTTP proxy");
            check(session.headers().firstValue("strict-transport-security").isPresent(), "Hosted HSTS");
        }
        System.out.println("Passed " + checks + " security checks against real Jetty endpoints.");
    }
}
