package com.plantstorage.controllers;

import com.google.gson.Gson;
import com.plantstorage.models.Photo;
import com.plantstorage.models.Plant;
import com.plantstorage.services.PhotoService;
import com.plantstorage.services.PlantService;
import com.plantstorage.services.GoogleDriveService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;

public class PlantServlet extends HttpServlet {

    private final PlantService plantService;
    private final PhotoService photoService;
    private final GoogleDriveService googleDriveService;
    private final Gson gson = new Gson();

    public PlantServlet(GoogleDriveService googleDriveService, String jsonFileId) {
        this.googleDriveService = googleDriveService;
        this.plantService = new PlantService(googleDriveService, jsonFileId);
        this.photoService = new PhotoService(googleDriveService, jsonFileId);
    }

    // ---------------------------------------------------------
    // GET → Load plants or load single plant (WITH PHOTOS)
    // ---------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json");

        String idParam = req.getParameter("id");

        if (idParam == null) {
            List<Plant> plants = plantService.getAllPlants();
            resp.getWriter().write(gson.toJson(plants));
        } else {
            int id = Integer.parseInt(idParam);
            Plant plant = plantService.getPlant(id);

            if (plant == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Plant not found\"}");
                return;
            }

            resp.getWriter().write(gson.toJson(plant));
        }
    }

    // ---------------------------------------------------------
    // POST → Add new plant
    // ---------------------------------------------------------
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        Plant newPlant = gson.fromJson(req.getReader(), Plant.class);
        plantService.addPlant(newPlant);

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"Plant added\"}");
    }

    // ---------------------------------------------------------
    // PUT → Update plant OR upload photo
    // ---------------------------------------------------------
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {

        // JSON PUT → Update plant
        if (req.getContentType() != null && req.getContentType().contains("application/json")) {

            int id = Integer.parseInt(req.getParameter("id"));

            Plant updated = gson.fromJson(req.getReader(), Plant.class);
            plantService.updatePlant(id, updated);

            resp.setContentType("application/json");
            resp.getWriter().write("{\"status\":\"Plant updated\"}");
            return;
        }

        // Multipart PUT → Upload photo to Google Drive
        int plantId = Integer.parseInt(req.getParameter("plantId"));
        Part filePart = req.getPart("photo");

        String fileName = Paths.get(filePart.getSubmittedFileName())
                .getFileName()
                .toString()
                .replaceAll("\\s+", "_");

        String contentType = filePart.getContentType();

        GoogleDriveService.UploadedPhoto uploaded;
        try (InputStream is = filePart.getInputStream()) {
            uploaded = googleDriveService.uploadPhoto(is, fileName, contentType);
        }

        Photo photo = new Photo();
        photo.setPlantId(plantId);
        photo.setGoogleFileId(uploaded.fileId);
        photo.setPublicUrl(uploaded.publicUrl);

        photoService.addPhoto(plantId, photo);

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"Photo uploaded\"}");
    }

    // ---------------------------------------------------------
    // DELETE → Delete plant
    // ---------------------------------------------------------
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String idParam = req.getParameter("id");

        if (idParam == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing id\"}");
            return;
        }

        int id = Integer.parseInt(idParam);

        boolean deleted = plantService.deletePlant(id);

        if (!deleted) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Plant not found\"}");
            return;
        }

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"Plant deleted\"}");
    }
}