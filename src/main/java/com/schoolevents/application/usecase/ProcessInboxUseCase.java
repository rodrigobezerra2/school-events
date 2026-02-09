package com.schoolevents.application.usecase;

import com.schoolevents.domain.model.EmailMessage;
import com.schoolevents.domain.model.Event;
import com.schoolevents.domain.port.out.AiEventExtractorPort;
import com.schoolevents.domain.port.out.EmailFetcherPort;
import com.schoolevents.domain.port.out.ProcessedEmailRepositoryPort;
import com.schoolevents.domain.service.EventReconciliationService;
import com.schoolevents.domain.exception.QuotaExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProcessInboxUseCase {
    private static final Logger logger = LoggerFactory.getLogger(ProcessInboxUseCase.class);

    private final EmailFetcherPort emailFetcher;
    private final AiEventExtractorPort aiExtractor;
    private final EventReconciliationService reconciliationService;
    private final ProcessedEmailRepositoryPort processedEmailRepository;

    public ProcessInboxUseCase(EmailFetcherPort emailFetcher,
            AiEventExtractorPort aiExtractor,
            EventReconciliationService reconciliationService,
            ProcessedEmailRepositoryPort processedEmailRepository) {
        this.emailFetcher = emailFetcher;
        this.aiExtractor = aiExtractor;
        this.reconciliationService = reconciliationService;
        this.processedEmailRepository = processedEmailRepository;
    }

    public void execute(boolean forceRescan) {
        logger.info("Starting inbox processing (Force Rescan: {})...", forceRescan);
        List<EmailMessage> emails = emailFetcher.fetchUnprocessedEmails();

        if (forceRescan) {
            logger.info("Force Rescan enabled. Will process all fetched emails regardless of history.");
        } else {
            // Filter emails that have already been processed
            emails = emails.stream()
                    .filter(email -> !processedEmailRepository.isProcessed(email.id()))
                    .toList();
        }

        int totalEmailsScanned = emails.size();
        logger.info("Found {} emails to process.", totalEmailsScanned);

        int newEventsCreated = 0;
        int eventsUpdated = 0;
        int eventsCancelled = 0;
        int failures = 0;

        int batchSize = 5;
        for (int i = 0; i < emails.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, emails.size());
            List<EmailMessage> batch = emails.subList(i, toIndex);

            try {
                List<Event> extracted = aiExtractor.extractEvents(batch);
                logger.info("Extracted {} events from batch.", extracted.size());

                for (Event event : extracted) {
                    EventReconciliationService.ReconciliationResult result = reconciliationService.reconcile(event);
                    switch (result) {
                        case CREATED -> newEventsCreated++;
                        case UPDATED -> eventsUpdated++;
                        case CANCELLED -> eventsCancelled++;
                    }
                }

                for (EmailMessage email : batch) {
                    processedEmailRepository.markAsProcessed(email.id());
                }
            } catch (QuotaExhaustedException e) {
                logger.error("Quota exhausted. Stopping run. Summary: {}", e.getMessage());
                break; // Stop immediately to protect account
            } catch (Exception e) {
                logger.error("Failed to process batch: {}", e.getMessage());
                failures += batch.size();
            }
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("            PROCESSING SUMMARY");
        System.out.println("--------------------------------------------------");
        System.out.printf("New Emails Scanned:     %d%n", totalEmailsScanned);
        System.out.printf("New Events Created:     %d%n", newEventsCreated);
        System.out.printf("Events Updated:         %d%n", eventsUpdated);
        if (eventsCancelled > 0) {
            System.out.printf("Events Cancelled:       %d%n", eventsCancelled);
        }
        System.out.printf("Processing Failures:    %d%n", failures);
        System.out.println("--------------------------------------------------");

        logger.info("Inbox processing complete.");
    }

}
