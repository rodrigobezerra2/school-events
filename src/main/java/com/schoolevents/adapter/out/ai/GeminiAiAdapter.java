package com.schoolevents.adapter.out.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolevents.domain.model.EmailMessage;
import com.schoolevents.domain.model.Event;
import com.schoolevents.domain.port.out.AiEventExtractorPort;
import com.schoolevents.domain.exception.QuotaExhaustedException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class GeminiAiAdapter implements AiEventExtractorPort {

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";
    private final String apiKey;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final int MAX_REQUESTS_PER_RUN = 15; // Limit to 15 to stay within safe bounds of daily/minute quotas

    public GeminiAiAdapter(String apiKey, boolean enabled) {
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // For JavaTime
    }

    @Override
    public List<Event> extractEvents(List<EmailMessage> emails) {
        if (!enabled || emails.isEmpty()) {
            return Collections.emptyList();
        }

        // Rate Limiting Strategy:
        // Free Tier Limit: 15 Requests Per Minute (RPM).
        // With batching, we significantly reduce the number of requests.
        try {
            Thread.sleep(2000); // 2s gap between batches
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (requestCount.incrementAndGet() > MAX_REQUESTS_PER_RUN) {
            System.err.println("Max AI requests reached (" + MAX_REQUESTS_PER_RUN + "). Skipping batch of "
                    + emails.size() + " emails.");
            return Collections.emptyList();
        }

        System.out.println("Processing " + emails.size() + " emails with Gemini AI...");
        emails.forEach(e -> System.out.println(" - Including Email: " + e.subject() + " (ID: " + e.id() + ")"));

        try {
            String payload = buildJsonPayload(emails);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Gemini AI extraction successful.");
                return parseResponse(response.body(), emails);
            } else if (response.statusCode() == 429) {
                System.err.println("Gemini API Quota Error (429): " + response.body());
                throw new QuotaExhaustedException("Gemini API Quota Exhausted (429)");
            } else {
                System.err.println("Gemini API Error: " + response.statusCode() + " - " + response.body());
                System.err.println("Full response body: " + response.body());
                return Collections.emptyList();
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to call Gemini API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildPrompt(List<EmailMessage> emails) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract school events from the following emails.\n");
        sb.append("Return ONLY a raw JSON array of objects. Do not include markdown formatting.\n\n");
        sb.append("JSON Format:\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"emailId\": \"String (Exactly matching the ID provided below)\",\n");
        sb.append("    \"title\": \"String\",\n");
        sb.append("    \"startDate\": \"ISO-8601 DateTime (yyyy-MM-ddTHH:mm:ss)\",\n");
        sb.append("    \"endDate\": \"ISO-8601 DateTime (Crucial for multi-day events like 'Half Term')\",\n");
        sb.append("    \"allDay\": boolean,\n");
        sb.append("    \"notes\": \"String\",\n");
        sb.append("    \"confidence\": Double (0.0-1.0),\n");
        sb.append("    \"status\": \"SCHEDULED\",\n");
        sb.append("    \"isRecurring\": boolean\n");
        sb.append("  }\n");
        sb.append("]\n\n");
        sb.append("IMPORTANT INSTRUCTIONS:\n");
        sb.append(
                "- If an event covers a date range (e.g., '16.2.26 - 20.2.26'), set startDate to the first day and endDate to the last day.\n");
        sb.append(
                "- RECURRING EVENTS: You MUST identify recurring patterns mentioned in the text (e.g., 'every Tuesday', 'weekly on Mondays').\n");
        sb.append(
                "- For each such pattern, expand it into individual event instances starting from the current date until August 1st 2026.\n");
        sb.append("- For these expanded instances, set 'isRecurring' to true.\n");
        sb.append("- If an event is specifically marked as a one-off in the text, do not repeat it.\n");
        sb.append(
                "- Ensure the 'emailId' property in the JSON matches the 'ID' field provided in the input exactly.\n\n");

        for (EmailMessage email : emails) {
            String content = email.plainTextBody();
            if (content == null || content.isBlank()) {
                content = email.htmlBody();
            }
            if (content == null)
                content = "";
            String bodySample = content.length() > 2000 ? content.substring(0, 2000) : content;

            sb.append("--- Email Start ---\n");
            sb.append("ID: ").append(email.id()).append("\n");
            sb.append("Subject: ").append(email.subject()).append("\n");
            sb.append("Body: ").append(bodySample).append("\n");
            sb.append("--- Email End ---\n\n");
        }

        return sb.toString();
    }

    private String buildJsonPayload(List<EmailMessage> emails) {
        try {
            String prompt = buildPrompt(emails);

            List<Object> parts = new ArrayList<>();
            parts.add(Collections.singletonMap("text", prompt));

            for (EmailMessage email : emails) {
                for (EmailMessage.AttachmentMetadata att : email.attachments()) {
                    if (att.mimeType() != null && att.mimeType().toLowerCase().startsWith("application/pdf")
                            && att.data() != null) {
                        String base64Data = Base64.getEncoder().encodeToString(att.data());
                        parts.add(Collections.singletonMap("inline_data",
                                java.util.Map.of("mime_type", "application/pdf", "data", base64Data)));
                    }
                }
            }

            return objectMapper.writeValueAsString(Collections.singletonMap("contents",
                    Collections.singletonList(Collections.singletonMap("parts", parts))));
        } catch (IOException e) {
            return "{}";
        }
    }

    private List<Event> parseResponse(String responseBody, List<EmailMessage> sourceEmails) {
        try {
            java.util.Map<String, EmailMessage> emailMap = sourceEmails.stream()
                    .collect(java.util.stream.Collectors.toMap(EmailMessage::id, e -> e));

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String text = parts.get(0).path("text").asText();
                    text = text.replace("```json", "").replace("```", "").trim();

                    List<EventDto> dtos = objectMapper.readValue(text, new TypeReference<List<EventDto>>() {
                    });

                    return dtos.stream().map(dto -> {
                        EmailMessage email = emailMap.get(dto.emailId);

                        // Defensive: Try matching without brackets if first attempt fails
                        if (email == null && dto.emailId != null) {
                            String unbracketed = dto.emailId.replace("<", "").replace(">", "");
                            email = sourceEmails.stream()
                                    .filter(e -> e.id().replace("<", "").replace(">", "").equals(unbracketed))
                                    .findFirst().orElse(null);
                        }

                        return new Event(
                                java.util.UUID.randomUUID().toString(),
                                dto.title != null ? dto.title : "Untitled Event",
                                dto.startDate,
                                dto.endDate,
                                dto.allDay,
                                dto.notes,
                                dto.confidence,
                                dto.status != null ? dto.status : Event.Status.ACTIVE,
                                dto.isRecurring,
                                email != null ? email.id() : dto.emailId,
                                email != null ? email.subject() : "Unknown Source",
                                email != null ? email.receivedAt() : null);
                    }).toList();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response: " + e.getMessage());
            System.err.println("Debug Body: " + responseBody);
        }
        return Collections.emptyList();
    }

    // Checking DTO for Parsing
    private static class EventDto {
        public String title;
        public LocalDateTime startDate;
        public LocalDateTime endDate;
        public boolean allDay;
        public String notes;
        public Double confidence;
        public Event.Status status;
        public String emailId;
        public boolean isRecurring;
    }
}
