package com.schoolevents.infrastructure.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private final Map<String, String> envVars = new HashMap<>();

    public ConfigLoader() {
        loadDotEnv();
    }

    private void loadDotEnv() {
        File dotEnv = new File(".env");
        if (!dotEnv.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dotEnv))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int sep = line.indexOf('=');
                if (sep > 0) {
                    String key = line.substring(0, sep).trim();
                    String value = line.substring(sep + 1).trim();
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    envVars.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read .env file: " + e.getMessage());
        }
    }

    public String get(String key) {
        // .env takes precedence, then system env
        String value = envVars.get(key);
        if (value == null) {
            value = System.getenv(key);
        }
        return value;
    }

    public String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
