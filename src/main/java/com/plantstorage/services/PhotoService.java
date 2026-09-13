package com.plantstorage.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.plantstorage.models.Photo;
import com.plantstorage.models.Plant;
import com.plantstorage.models.PlantStorage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PhotoService {

    private final GoogleDriveService drive;
    private final String jsonFileId;
    private final Gson gson = new Gson();

    public PhotoService(GoogleDriveService drive, String jsonFileId) {
        this.drive = drive;
        this.jsonFileId = jsonFileId;
    }

    // ---------------------------------------------------------
    // Load JSON safely
    // ---------------------------------------------------------
    private List<Plant> loadPlants() {
        String json = drive.downloadFile(jsonFileId);

        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<PlantStorage>() {}.getType();
        PlantStorage storage = gson.fromJson(json, type);

        if (storage == null || storage.plants == null) {
            return new ArrayList<>();
        }

        return storage.plants;
    }

    // ---------------------------------------------------------
    // Save JSON
    // ---------------------------------------------------------
    private void savePlants(List<Plant> plants) {
        PlantStorage storage = new PlantStorage(plants);
        String json = gson.toJson(storage);
        drive.uploadOrUpdateFile(jsonFileId, json);
    }

    // ---------------------------------------------------------
    // Add photo to plant
    // ---------------------------------------------------------
    public void addPhoto(int plantId, Photo photo) {
        List<Plant> plants = loadPlants();

        for (Plant p : plants) {
            if (p.getId() == plantId) {

                if (p.getPhotos() == null) {
                    p.setPhotos(new ArrayList<>());
                }

                // Assign unique photo ID
                int newPhotoId = p.getPhotos().isEmpty()
                        ? 1
                        : p.getPhotos().get(p.getPhotos().size() - 1).getId() + 1;

                photo.setId(newPhotoId);

                p.getPhotos().add(photo);
                break;
            }
        }

        savePlants(plants);
    }

    // ---------------------------------------------------------
    // Get photos for plant
    // ---------------------------------------------------------
    public List<Photo> getPhotosForPlant(int plantId) {
        List<Plant> plants = loadPlants();

        for (Plant p : plants) {
            if (p.getId() == plantId) {
                return p.getPhotos() != null ? p.getPhotos() : new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    // ---------------------------------------------------------
    // Delete photo
    // ---------------------------------------------------------
    public void deletePhoto(int plantId, int photoId) {
        List<Plant> plants = loadPlants();

        for (Plant p : plants) {
            if (p.getId() == plantId && p.getPhotos() != null) {
                p.getPhotos().removeIf(photo -> photo.getId() == photoId);
                break;
            }
        }

        savePlants(plants);
    }
}