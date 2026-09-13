package com.plantstorage.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class GoogleDriveService {

    private final Drive drive;

    public GoogleDriveService() {

        String clientId = System.getenv("GOOGLE_CLIENT_ID");
        String clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        String refreshToken = System.getenv("GOOGLE_REFRESH_TOKEN");

        if (clientId == null || clientSecret == null || refreshToken == null) {
            throw new RuntimeException("Missing Google OAuth environment variables.");
        }

        try {
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setRefreshToken(refreshToken);

            this.drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential
            ).setApplicationName("PlantStorage").build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Google Drive client", e);
        }
    }

    // ---------------------------------------------------------
    // CREATE FOLDER
    // ---------------------------------------------------------
    public String createFolder(String name) {
        try {
            File fileMetadata = new File();
            fileMetadata.setName(name);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");

            File folder = drive.files()
                    .create(fileMetadata)
                    .setFields("id")
                    .execute();

            return folder.getId();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // CREATE JSON FILE
    // ---------------------------------------------------------
    public String createJsonFile(String folderId, String name, String content) {
        try {
            File fileMetadata = new File();
            fileMetadata.setName(name);
            fileMetadata.setParents(Collections.singletonList(folderId));
            fileMetadata.setMimeType("application/json");

            ByteArrayContent fileContent =
                    new ByteArrayContent("application/json", content.getBytes(StandardCharsets.UTF_8));

            File file = drive.files()
                    .create(fileMetadata, fileContent)
                    .setFields("id")
                    .execute();

            return file.getId();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create JSON file: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // DOWNLOAD JSON FILE
    // ---------------------------------------------------------
    public String downloadFile(String fileId) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            drive.files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(outputStream);

            return outputStream.toString(StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // UPDATE JSON FILE
    // ---------------------------------------------------------
    public void uploadOrUpdateFile(String fileId, String content) {
        try {
            ByteArrayContent fileContent =
                    new ByteArrayContent("application/json", content.getBytes(StandardCharsets.UTF_8));

            drive.files()
                    .update(fileId, null, fileContent)
                    .execute();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update file: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // UPLOAD PHOTO INTO THE CLIENT FOLDER
    // ---------------------------------------------------------
    public UploadedPhoto uploadPhoto(InputStream stream, String fileName, String contentType) {

        try {
            File fileMetadata = new File();
            fileMetadata.setName(fileName);

            String folderId = System.getenv("GOOGLE_FOLDER_ID");
            if (folderId == null || folderId.isBlank()) throw new IllegalStateException("Missing GOOGLE_FOLDER_ID");
            fileMetadata.setParents(Collections.singletonList(folderId));

            InputStreamContent mediaContent =
                    new InputStreamContent(contentType, stream);

            File uploadedFile = drive.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            // Photos are served through the authenticated /photos endpoint.
            // Do not grant public Drive permissions to new uploads.
            return new UploadedPhoto(uploadedFile.getId(), null);

        } catch (Exception ex) {
            throw new RuntimeException("Google Drive upload failed: " + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------
    // DELETE PHOTO
    // ---------------------------------------------------------
    public void deletePhoto(String fileId) {
        try {
            drive.files().delete(fileId).execute();
        } catch (Exception ex) {
            throw new RuntimeException("Google Drive delete failed: " + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------
    // DTO
    // ---------------------------------------------------------
    public static class UploadedPhoto {
        public final String fileId;
        public final String publicUrl;

        public UploadedPhoto(String fileId, String publicUrl) {
            this.fileId = fileId;
            this.publicUrl = publicUrl;
        }
    }
    public String getPhotoMimeType(String fileId) throws IOException {
        return drive.files()
                .get(fileId)
                .setFields("mimeType")
                .execute()
                .getMimeType();
    }

    public void downloadPhoto(String fileId, OutputStream output)
            throws IOException {
        drive.files()
                .get(fileId)
                .executeMediaAndDownloadTo(output);
    }
}
