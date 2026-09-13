package com.plantstorage.security;

import java.net.URI;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public record AuthSettings(String username, String passwordHash, String origin, int idleSeconds) {
    public AuthSettings {
        if (username == null || username.isBlank() || username.length() > 100)
            throw new IllegalArgumentException("Set APP_USERNAME or run ConfigureLogin for local use.");
        PasswordHash.validate(passwordHash);
        URI uri;
        try { uri = URI.create(origin); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("APP_BASE_URL must be a complete URL."); }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
            uri.getRawFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("APP_BASE_URL must contain only the scheme, hostname and optional port.");
        boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if (!("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("A public APP_BASE_URL must use HTTPS.");
        origin = uri.getScheme() + "://" + uri.getRawAuthority();
        if (idleSeconds < 60 || idleSeconds > 3600) throw new IllegalArgumentException("Invalid idle timeout");
    }
    public boolean secureCookies() { return origin.startsWith("https://"); }
    public static AuthSettings load(int port) throws IOException {
        String origin = System.getenv("APP_BASE_URL");
        if (origin == null || origin.isBlank()) origin = System.getenv("RENDER_EXTERNAL_URL");
        boolean hosted = System.getenv("RENDER") != null;
        if (origin == null || origin.isBlank()) {
            if (hosted) throw new IllegalArgumentException("Set APP_BASE_URL to the HTTPS address of the hosted app.");
            origin = "http://localhost:" + port;
        }
        String username = System.getenv("APP_USERNAME");
        String hash = System.getenv("APP_PASSWORD_HASH");
        // Local setup contains a hash, never a plaintext password; it is excluded from Docker/Git.
        if (username == null && hash == null && !hosted && origin.startsWith("http://localhost:") &&
            Files.isRegularFile(Path.of(".login.properties"))) {
            Properties local = new Properties();
            try (Reader reader = Files.newBufferedReader(Path.of(".login.properties"))) { local.load(reader); }
            username = local.getProperty("APP_USERNAME"); hash = local.getProperty("APP_PASSWORD_HASH");
        }
        return new AuthSettings(username, hash, origin, 30 * 60);
    }
}
