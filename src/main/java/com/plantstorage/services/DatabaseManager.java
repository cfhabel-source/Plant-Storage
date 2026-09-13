package com.plantstorage.services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;


public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:plantstorage.db";

    public DatabaseManager() {
        createTableIfNotExists();
    }

    public Connection getConnection() throws SQLException {
        System.out.println("ABSOLUTE DB PATH: " + new File("plantstorage.db").getAbsolutePath());
        return DriverManager.getConnection("jdbc:sqlite:plantstorage.db");
    }

    private void createTableIfNotExists() {
        String plantTable = """
        CREATE TABLE IF NOT EXISTS plants (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            species TEXT,
            location TEXT,
            notes TEXT
        );
    """;

        String photoTable = """
        CREATE TABLE IF NOT EXISTS photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            plant_id INTEGER NOT NULL,
            file_path TEXT NOT NULL,
            FOREIGN KEY (plant_id) REFERENCES plants(id)
        );
    """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(plantTable);
            stmt.execute(photoTable);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("USING DB FILE: " + DB_URL);
    }
}
