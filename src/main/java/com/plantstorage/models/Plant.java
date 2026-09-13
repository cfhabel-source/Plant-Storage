package com.plantstorage.models;

import java.util.List;

public class Plant {

    private int id;
    private String name;
    private String species;
    private String location;
    private String acquiredAt;
    private String careInstructions;
    private String notes;

    private List<Photo> photos;

    // ---------------------------------------------------------
    // REQUIRED: No-argument constructor for Gson
    // ---------------------------------------------------------
    public Plant() {}

    // ---------------------------------------------------------
    // Full constructor
    // ---------------------------------------------------------
    public Plant(int id, String name, String species, String location,
                 String acquiredAt, String careInstructions, String notes) {
        this.id = id;
        this.name = name;
        this.species = species;
        this.location = location;
        this.acquiredAt = acquiredAt;
        this.careInstructions = careInstructions;
        this.notes = notes;
    }

    // ---------------------------------------------------------
    // Constructor for new plants (id auto-assigned)
    // ---------------------------------------------------------
    public Plant(String name, String species, String location,
                 String acquiredAt, String careInstructions, String notes) {
        this(-1, name, species, location, acquiredAt, careInstructions, notes);
    }

    // ---------------------------------------------------------
    // GETTERS
    // ---------------------------------------------------------
    public int getId() { return id; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public String getLocation() { return location; }
    public String getAcquiredAt() { return acquiredAt; }
    public String getCareInstructions() { return careInstructions; }
    public String getNotes() { return notes; }
    public List<Photo> getPhotos() { return photos; }

    // ---------------------------------------------------------
    // SETTERS
    // ---------------------------------------------------------
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSpecies(String species) { this.species = species; }
    public void setLocation(String location) { this.location = location; }
    public void setAcquiredAt(String acquiredAt) { this.acquiredAt = acquiredAt; }
    public void setCareInstructions(String careInstructions) { this.careInstructions = careInstructions; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setPhotos(List<Photo> photos) { this.photos = photos; }
}