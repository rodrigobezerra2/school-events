package com.schoolevents.adapter.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolevents.domain.model.Event;
import com.schoolevents.domain.port.out.EventRepositoryPort;
import com.schoolevents.domain.port.out.StoragePort;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonExporter {
    private final EventRepositoryPort eventRepository;
    private final ObjectMapper objectMapper;
    private final String outputPath;
    private final StoragePort storagePort;
    private final String uiPassword;

    public JsonExporter(EventRepositoryPort eventRepository, String outputPath, StoragePort storagePort,
            String uiPassword) {
        this.eventRepository = eventRepository;
        this.outputPath = outputPath;
        this.storagePort = storagePort;
        this.uiPassword = uiPassword;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void export() {
        List<Event> events = eventRepository.findAll();

        // 0. Merge with manual_events.json if it exists
        File manualFile = new File("manual_events.json");
        if (manualFile.exists()) {
            System.out.println("Merging manual_events.json...");
            try {
                List<Event> manualEvents = objectMapper.readValue(manualFile,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Event.class));
                events.addAll(manualEvents);
            } catch (IOException e) {
                System.err.println("Warning: Could not read manual_events.json: " + e.getMessage());
            }
        }

        File file = new File(outputPath);
        file.getParentFile().mkdirs(); // Ensure directory exists
        try {
            // 1. Prepare Content
            String jsonContent = objectMapper.writeValueAsString(events);
            boolean isEncrypted = false;

            if (uiPassword != null && !uiPassword.isBlank()) {
                System.out.println("Encrypting data with UI Password...");
                try {
                    jsonContent = com.schoolevents.infrastructure.security.AesEncryptionUtil.encrypt(jsonContent,
                            uiPassword);
                    isEncrypted = true;
                } catch (Exception e) {
                    System.err.println("Encryption failed: " + e.getMessage());
                    // Fallback to unencrypted for local if encryption fails?
                    // No, for GitHub Pages we want safety.
                }
            }

            // 2. Write Local Files
            java.nio.file.Files.writeString(file.toPath(), jsonContent);
            System.out.println("Exported " + events.size() + " events to local file " +
                    (isEncrypted ? "(ENCRYPTED)" : "(PLAIN)") + ": " + file.getAbsolutePath());

            // 2b. Secondary Unencrypted Export (for inspection)
            if (isEncrypted) {
                File plainFile = new File(file.getParent(), "events_plain.json");
                java.nio.file.Files.writeString(plainFile.toPath(), objectMapper.writeValueAsString(events));
                System.out.println("Exported PLAIN events to: " + plainFile.getAbsolutePath());
            }

            // 2. Upload to Cloud (if enabled)
            if (storagePort != null) {
                System.out.println("Uploading to Storage...");
                storagePort.upload("events.json", jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

        } catch (IOException e) {
            System.err.println("Failed to export events: " + e.getMessage());
        }
    }
}
