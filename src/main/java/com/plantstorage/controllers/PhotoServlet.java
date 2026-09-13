package com.plantstorage.controllers;

import com.plantstorage.models.Photo;
import com.plantstorage.services.PhotoService;
import com.plantstorage.services.GoogleDriveService;
import com.google.gson.Gson;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class PhotoServlet extends HttpServlet {

    private final PhotoService photoService;
    private final GoogleDriveService googleDriveService;
    private final Gson gson = new Gson();

    public PhotoServlet(GoogleDriveService googleDriveService, String jsonFileId) {
        this.googleDriveService = googleDriveService;
        this.photoService = new PhotoService(googleDriveService, jsonFileId);
    }

    // ---------------------------------------------------------
    // GET PHOTOS FOR PLANT
    // /photos?plantId=123
    // ---------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int plantId;
        Integer photoId = null;

        try {
            plantId = Integer.parseInt(req.getParameter("plantId"));

            String idParam = req.getParameter("id");
            if (idParam != null) {
                photoId = Integer.parseInt(idParam);
            }
        } catch (NumberFormatException ex) {
            resp.sendError(400, "Valid plantId and photo id required");
            return;
        }

        try {
            List<Photo> photos = photoService.getPhotosForPlant(plantId);

            // No photo ID: return the photo list as before.
            if (photoId == null) {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write(gson.toJson(photos));
                return;
            }

            Photo target = null;

            for (Photo photo : photos) {
                if (photo.getId() == photoId) {
                    target = photo;
                    break;
                }
            }

            if (target == null || target.getGoogleFileId() == null) {
                resp.sendError(404, "Photo not found");
                return;
            }

            String fileId = target.getGoogleFileId();
            String mimeType = googleDriveService.getPhotoMimeType(fileId);

            // Allow common browser-displayable raster formats.
            if (!List.of(
                    "image/jpeg", "image/png", "image/gif",
                    "image/webp", "image/avif"
            ).contains(mimeType == null ? "" : mimeType)) {
                resp.sendError(415, "Unsupported image format");
                return;
            }

            resp.setContentType(mimeType);
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("Cache-Control", "private, max-age=300");

            googleDriveService.downloadPhoto(fileId, resp.getOutputStream());

        } catch (IOException | RuntimeException ex) {
            getServletContext().log("Failed to load photo from Google Drive", ex);

            if (!resp.isCommitted()) {
                resp.reset();
                resp.sendError(502, "Could not load photo from Google Drive");
            }
        }
    }

    // ---------------------------------------------------------
    // DELETE PHOTO
    // /photos?plantId=123&id=456
    // ---------------------------------------------------------
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String plantIdParam = req.getParameter("plantId");
        String photoIdParam = req.getParameter("id");

        if (plantIdParam == null || photoIdParam == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing plantId or id\"}");
            return;
        }

        int plantId = Integer.parseInt(plantIdParam);
        int photoId = Integer.parseInt(photoIdParam);

        // Get photos for plant
        List<Photo> photos = photoService.getPhotosForPlant(plantId);

        Photo target = photos.stream()
                .filter(p -> p.getId() == photoId)
                .findFirst()
                .orElse(null);

        if (target == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Photo not found\"}");
            return;
        }

        // Delete from Google Drive
        try {
            googleDriveService.deletePhoto(target.getGoogleFileId());
        } catch (Exception ex) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Failed to delete from Google Drive\"}");
            return;
        }

        // Delete from JSON
        photoService.deletePhoto(plantId, photoId);

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"Photo deleted\"}");
    }
}