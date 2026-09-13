package com.plantstorage.security;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Run directly in a terminal so password entry is hidden and not in shell history. */
public final class ConfigureLogin {
    public static void main(String[] args) throws IOException {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run directly in PowerShell: java -cp build/classes/java/main com.plantstorage.security.ConfigureLogin");
        Path output = Path.of(".login.properties");
        if (Files.exists(output) && !"yes".equalsIgnoreCase(console.readLine("Replace the existing local login? Type yes: "))) return;
        String username = console.readLine("Client username: ");
        if (username == null || !username.matches("[A-Za-z0-9._-]{1,100}"))
            throw new IllegalArgumentException("Use 1–100 letters, numbers, dots, underscores or hyphens for the username.");
        char[] first = console.readPassword("Password/passphrase (at least 12 characters): ");
        char[] second = console.readPassword("Repeat password: ");
        try {
            if (first == null || second == null || !Arrays.equals(first, second)) throw new IllegalArgumentException("Passwords did not match.");
            String hash = PasswordHash.create(first);
            // Safe characters only, so no property escaping is required.
            Files.writeString(output, "APP_USERNAME=" + username + "\nAPP_PASSWORD_HASH=" + hash + "\n");
            console.printf("Saved local login in .login.properties. The password itself was not saved.%n");
            console.printf("For Render, copy these TWO values into its environment settings:%nAPP_USERNAME=%s%nAPP_PASSWORD_HASH=%s%n", username, hash);
            console.printf("Use the SAME password to sign in. Do not commit .login.properties.%n");
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
        }
    }
}
