package com.plantstorage;

import com.plantstorage.controllers.IdentificationServlet;
import com.plantstorage.controllers.PhotoServlet;
import com.plantstorage.controllers.PlantServlet;
import com.plantstorage.services.GoogleDriveService;
import com.plantstorage.security.AuthSettings;
import com.plantstorage.security.SecuritySetup;
import org.eclipse.jetty.util.resource.ResourceFactory;

import jakarta.servlet.MultipartConfigElement;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        AuthSettings auth = AuthSettings.load(port);
        requireEnv("GOOGLE_CLIENT_ID");
        requireEnv("GOOGLE_CLIENT_SECRET");
        requireEnv("GOOGLE_REFRESH_TOKEN");
        requireEnv("GOOGLE_FOLDER_ID");
        String jsonFileId = requireEnv("GOOGLE_JSON_FILE_ID");

        Path webRoot = Path.of(System.getenv().getOrDefault("WEB_ROOT", "src/main/webapp")).toAbsolutePath();

        if (!Files.isRegularFile(webRoot.resolve("index.html"))) {
            throw new IllegalStateException(
                    "Cannot find index.html in " + webRoot
            );
        }

        GoogleDriveService drive = new GoogleDriveService();

        // Check Drive access before starting the server.
        // This reads the existing file without changing it.
        drive.downloadFile(jsonFileId);
        System.out.println("Google Drive JSON file is accessible.");

        Server server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setHost(System.getenv().getOrDefault("APP_HOST", System.getenv("PORT") == null ? "127.0.0.1" : "0.0.0.0"));
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context =
                new ServletContextHandler(ServletContextHandler.SESSIONS);

        context.setContextPath("/");
        context.setBaseResource(ResourceFactory.of(context).newResource(webRoot));
        context.setWelcomeFiles(new String[]{"index.html"});
        SecuritySetup.install(context, auth);
        server.setHandler(context);

        ServletHolder plantHolder =
                new ServletHolder(new PlantServlet(drive, jsonFileId));

        context.addServlet(plantHolder, "/plants/*");

        plantHolder.getRegistration().setMultipartConfig(
                new MultipartConfigElement(
                        System.getProperty("java.io.tmpdir"),
                        10L * 1024 * 1024, // Maximum file size: 10 MB
                        12L * 1024 * 1024, // Maximum request size: 12 MB
                        1024 * 1024        // Memory threshold: 1 MB
                )
        );

        context.addServlet(
                new ServletHolder(new PhotoServlet(drive, jsonFileId)),
                "/photos/*"
        );

        context.addServlet(new ServletHolder(new IdentificationServlet()), "/identify/*");

        ServletHolder staticFiles = new ServletHolder(DefaultServlet.class);
        staticFiles.setInitParameter("dirAllowed", "false");
        context.addServlet(staticFiles, "/");

        server.setStopAtShutdown(true);
        server.start();

        System.out.println("PlantStorage running at " + auth.origin());
        server.join();
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }

        return value;
    }
}