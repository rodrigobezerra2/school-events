package com.schoolevents.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolevents.adapter.out.ai.GeminiAiAdapter;
import com.schoolevents.adapter.out.email.EmailParser;
import com.schoolevents.domain.model.EmailMessage;
import com.schoolevents.domain.model.Event;
import com.schoolevents.infrastructure.config.ConfigLoader;
import jakarta.mail.*;
import jakarta.mail.search.SubjectTerm;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A manual test utility to fetch a specific email by subject and test
 * extraction.
 */
public class ManualTest {

    public static void main(String[] args) {
        System.out.println("=== Manual Extraction Test ===");

        ConfigLoader config = new ConfigLoader();
        String gmailUsername = config.get("GMAIL_USERNAME");
        String gmailPassword = config.get("GMAIL_PASSWORD");
        String geminiApiKey = config.get("GEMINI_API_KEY");

        // Target subject can be passed via command line or defaults to the newsletter
        // reported
        String targetSubject = args.length > 0 ? args[0] : "Fwd: CYLCHLYTHYR - IONAWR 2026 / NEWSLETTER - JANUARY 2026";
        System.out.println("Target Subject: " + targetSubject);

        if (gmailUsername == null || gmailPassword == null) {
            System.err.println("Error: GMAIL_USERNAME and GMAIL_PASSWORD must be set in .env or environment.");
            return;
        }

        try {
            // 1. Fetch Targeted Email
            List<EmailMessage> emails = fetchEmailBySubject(gmailUsername, gmailPassword, targetSubject);

            if (emails.isEmpty()) {
                System.out.println("Result: No email found with subject matching: " + targetSubject);
                System.out.println("Check if the subject is exact or try a partial search term.");
                return;
            }

            System.out.println("Found " + emails.size() + " email(s).");

            // 2. Setup AI Adapter
            GeminiAiAdapter aiAdapter = new GeminiAiAdapter(geminiApiKey, true);
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);

            List<Event> allExtractedEvents = new ArrayList<>();

            // 3. Process each found email
            for (EmailMessage email : emails) {
                System.out.println("\n" + "=".repeat(50));
                System.out.println("LOADED EMAIL REPORT");
                System.out.println("=".repeat(50));
                System.out.println("Subject:     " + email.subject());
                System.out.println("Sender:      " + email.sender());
                System.out.println("Received:    " + email.receivedAt());
                System.out.println("ID:          " + email.id());
                System.out.println("Body Size:   " + email.plainTextBody().length() + " chars");
                System.out.println("Attachments: " + email.attachments().size());
                for (EmailMessage.AttachmentMetadata att : email.attachments()) {
                    System.out.println(" - " + att.fileName() + " (" + att.mimeType() + ")");
                }

                System.out.println("\nRequesting AI extraction...");
                List<Event> events = aiAdapter.extractEvents(List.of(email));

                if (events.isEmpty()) {
                    System.out.println("FAILURE: No events extracted from this email.");
                } else {
                    System.out.println("SUCCESS: Extracted " + events.size() + " events.");
                    for (Event e : events) {
                        System.out.println(
                                " - [" + e.startDate() + "] " + e.title() + (e.isRecurring() ? " (RECURRING)" : ""));
                    }
                    allExtractedEvents.addAll(events);
                }
            }

            // 4. Save Temporary Unencrypted File
            if (!allExtractedEvents.isEmpty()) {
                File debugDir = new File("debug");
                if (!debugDir.exists())
                    debugDir.mkdirs();

                File outputFile = new File(debugDir, "manual_extraction.json");
                Files.writeString(outputFile.toPath(), mapper.writeValueAsString(allExtractedEvents));

                System.out.println("\n" + "=".repeat(50));
                System.out.println("SUMMARY");
                System.out.println("=".repeat(50));
                System.out.println("Total Events Extracted: " + allExtractedEvents.size());
                System.out.println("Saved PLAIN events to: " + outputFile.getAbsolutePath());
                System.out.println("Note: This folder is gitignored.");
            }

        } catch (Exception e) {
            System.err.println("\nFATAL ERROR during manual extraction:");
            e.printStackTrace();
        }
    }

    private static List<EmailMessage> fetchEmailBySubject(String username, String password, String subject)
            throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");

        List<EmailMessage> result = new ArrayList<>();
        EmailParser parser = new EmailParser();

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(username, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        System.out.println("Searching IMAP for subject like: *" + subject + "*");
        // SubjectTerm is usually substring match
        Message[] messages = inbox.search(new SubjectTerm(subject));

        for (Message msg : messages) {
            String[] headers = msg.getHeader("Message-ID");
            String messageId = (headers != null && headers.length > 0) ? headers[0]
                    : "unknown-" + System.currentTimeMillis();
            result.add(parser.parse(msg, messageId));
        }

        inbox.close(false);
        store.close();

        return result;
    }
}
