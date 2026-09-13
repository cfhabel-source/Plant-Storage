package com.plantstorage.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.plantstorage.models.Plant;
import com.plantstorage.models.PlantStorage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PlantService {

    private final GoogleDriveService drive;
    private final String jsonFileId;
    private final Gson gson = new Gson();

    public PlantService(GoogleDriveService drive, String jsonFileId) {
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
    // Add plant
    // ---------------------------------------------------------
    public void addPlant(Plant plant) {
        List<Plant> plants = loadPlants();

        int newId = plants.isEmpty() ? 1 : plants.get(plants.size() - 1).getId() + 1;
        plant.setId(newId);

        plants.add(plant);
        savePlants(plants);
    }

    // ---------------------------------------------------------
    // Get plant
    // ---------------------------------------------------------
    public Plant getPlant(int id) {
        return loadPlants().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------
    // Get all plants
    // ---------------------------------------------------------
    public List<Plant> getAllPlants() {
        return loadPlants();
    }

    // ---------------------------------------------------------
    // Update plant
    // ---------------------------------------------------------
    public boolean updatePlant(int id, Plant updated) {
        List<Plant> plants = loadPlants();

        for (Plant p : plants) {
            if (p.getId() == id) {
                updated.setId(id);
                updated.setPhotos(p.getPhotos()); // preserve photos
                plants.set(plants.indexOf(p), updated);
                savePlants(plants);
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------
    // Delete plant
    // ---------------------------------------------------------
    public boolean deletePlant(int id) {
        List<Plant> plants = loadPlants();
        boolean removed = plants.removeIf(p -> p.getId() == id);
        savePlants(plants);
        return removed;
    }
}
