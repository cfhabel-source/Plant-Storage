package com.plantstorage.models;

public class Photo {

    private int id;
    private int plantId;

    // Google Drive public URL
    private String publicUrl;

    // Google Drive file ID (used for deletion)
    private String googleFileId;

    // ---------------------------------------------------------
    // REQUIRED: No-argument constructor for Gson
    // ---------------------------------------------------------
    public Photo() {}

    // ---------------------------------------------------------
    // Full constructor (used when loading from JSON)
    // ---------------------------------------------------------
    public Photo(int id, int plantId, String googleFileId, String publicUrl) {
        this.id = id;
        this.plantId = plantId;
        this.googleFileId = googleFileId;
        this.publicUrl = publicUrl;
    }

    // ---------------------------------------------------------
    // Constructor for new photos (id auto-generated)
    // ---------------------------------------------------------
    public Photo(int plantId, String googleFileId, String publicUrl) {
        this(-1, plantId, googleFileId, publicUrl);
    }

    // ---------------------------------------------------------
    // Getters
    // ---------------------------------------------------------
    public int getId() {
        return id;
    }

    public int getPlantId() {
        return plantId;
    }

    public String getGoogleFileId() {
        return googleFileId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    // ---------------------------------------------------------
    // Setters
    // ---------------------------------------------------------
    public void setId(int id) {
        this.id = id;
    }

    public void setPlantId(int plantId) {
        this.plantId = plantId;
    }

    public void setGoogleFileId(String googleFileId) {
        this.googleFileId = googleFileId;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    // ---------------------------------------------------------
    // Debugging helper (optional but recommended)
    // ---------------------------------------------------------
    @Override
    public String toString() {
        return "Photo{" +
                "id=" + id +
                ", plantId=" + plantId +
                ", googleFileId='" + googleFileId + '\'' +
                ", publicUrl='" + publicUrl + '\'' +
                '}';
    }
}