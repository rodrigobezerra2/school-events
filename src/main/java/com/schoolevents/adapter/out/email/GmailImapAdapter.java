package com.schoolevents.adapter.out.email;

import com.schoolevents.domain.model.EmailMessage;
import com.schoolevents.domain.port.out.EmailFetcherPort;
import com.schoolevents.domain.port.out.ProcessedEmailRepositoryPort;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromTerm;
import jakarta.mail.internet.InternetAddress;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class GmailImapAdapter implements EmailFetcherPort {

    private final String username;
    private final String password;
    private final String senderFilter;
    private final ProcessedEmailRepositoryPort processedEmailRepository;
    private final EmailParser emailParser;

    public GmailImapAdapter(String username, String password, String senderFilter,
            ProcessedEmailRepositoryPort processedEmailRepository) {
        this.username = username;
        this.password = password;
        this.senderFilter = senderFilter;
        this.processedEmailRepository = processedEmailRepository;
        this.emailParser = new EmailParser();
    }

    @Override
    public List<EmailMessage> fetchUnprocessedEmails() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");

        List<EmailMessage> result = new ArrayList<>();

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages;
            if (senderFilter != null && !senderFilter.isBlank()) {
                System.out.println("Applying sender filter: " + senderFilter);
                messages = inbox.search(new FromTerm(new InternetAddress(senderFilter)));
            } else {
                // Fetch generic latest emails
                int totalMessages = inbox.getMessageCount();
                int start = Math.max(1, totalMessages - 199); // Fetch last 200
                int end = totalMessages;
                messages = totalMessages > 0 ? inbox.getMessages(start, end) : new Message[0];
            }

            // Iterate backwards
            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];
                String messageId = getMessageId(msg);

                if (messageId != null && !processedEmailRepository.isProcessed(messageId)) {
                    try {
                        result.add(emailParser.parse(msg, messageId));
                    } catch (Exception e) {
                        System.err.println("Failed to parse email " + messageId + ": " + e.getMessage());
                    }
                }
            }

            inbox.close(false);
            store.close();

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch emails", e);
        }

        return result;
    }

    private String getMessageId(Message msg) {
        try {
            String[] headers = msg.getHeader("Message-ID");
            return (headers != null && headers.length > 0) ? headers[0] : null;
        } catch (MessagingException e) {
            return null;
        }
    }
}
