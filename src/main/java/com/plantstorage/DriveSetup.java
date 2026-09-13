package com.plantstorage;

import com.plantstorage.services.GoogleDriveService;

public class DriveSetup {
    public static void main(String[] args) {
        GoogleDriveService drive = new GoogleDriveService();

        String folderId = drive.createFolder("PlantStorage");
        System.out.println("GOOGLE_FOLDER_ID=" + folderId);

        String jsonFileId = drive.createJsonFile(
                folderId,
                "plantstorage.json",
                "{\"plants\":[]}"
        );
        System.out.println("GOOGLE_JSON_FILE_ID=" + jsonFileId);
    }
}