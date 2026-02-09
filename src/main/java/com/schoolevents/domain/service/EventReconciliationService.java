package com.schoolevents.domain.service;

import com.schoolevents.domain.model.Event;
import com.schoolevents.domain.port.out.EventRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventReconciliationService {

    private final EventRepositoryPort eventRepository;

    public EventReconciliationService(EventRepositoryPort eventRepository) {
        this.eventRepository = eventRepository;
    }

    public enum ReconciliationResult {
        CREATED, UPDATED, CANCELLED, NO_ACTION
    }

    private static final Logger logger = LoggerFactory.getLogger(EventReconciliationService.class);

    public ReconciliationResult reconcile(Event incomingEvent) {
        // Find all events on the same day
        List<Event> existingEventsOnDay = eventRepository.findByDate(incomingEvent.startDate());

        // Find ALL equivalent events already in DB
        List<Event> matches = existingEventsOnDay.stream()
                .filter(existing -> {
                    boolean result = isSameEvent(existing, incomingEvent);
                    if (result) {
                        logger.info("Match found: '{}' matches existing '{}' on {}",
                                incomingEvent.title(), existing.title(), incomingEvent.startDate());
                    }
                    return result;
                })
                .collect(java.util.stream.Collectors.toList());

        if (!matches.isEmpty()) {
            // Select the "Master" record (usually the first one, but we'll update it)
            Event master = matches.get(0);

            // DELETE all other matches to clean up existing duplicates
            for (int i = 1; i < matches.size(); i++) {
                logger.info("Deleting duplicate event: '{}' (ID: {})", matches.get(i).title(), matches.get(i).id());
                eventRepository.delete(matches.get(i).id());
            }

            // Always update if the incoming event is from a newer email (or same/unknown
            // time)
            boolean isNewer = incomingEvent.sourceEmailReceivedAt() == null ||
                    master.sourceEmailReceivedAt() == null ||
                    !incomingEvent.sourceEmailReceivedAt().isBefore(master.sourceEmailReceivedAt());

            if (incomingEvent.status() == Event.Status.CANCELLED) {
                cancelEvent(master, incomingEvent);
                return ReconciliationResult.CANCELLED;
            } else if (isNewer) {
                updateEvent(master, incomingEvent);
                return ReconciliationResult.UPDATED;
            } else {
                return ReconciliationResult.NO_ACTION;
            }
        } else {
            if (createNewEvent(incomingEvent)) {
                return ReconciliationResult.CREATED;
            }
            return ReconciliationResult.NO_ACTION;
        }
    }

    private void cancelEvent(Event existing, Event incoming) {
        Event cancelled = new Event(
                existing.id(),
                existing.title(),
                existing.startDate(),
                existing.endDate(),
                existing.allDay(),
                existing.notes(),
                existing.confidence(),
                Event.Status.CANCELLED,
                existing.isRecurring(),
                incoming.sourceEmailId(),
                incoming.sourceEmailSubject(),
                incoming.sourceEmailReceivedAt());
        eventRepository.save(cancelled);
    }

    private void updateEvent(Event existing, Event incoming) {
        // We update fields with incoming data, assuming it's newer/better
        Event updated = new Event(
                existing.id(),
                incoming.title(),
                incoming.startDate(),
                incoming.endDate(),
                incoming.allDay(),
                incoming.notes(),
                incoming.confidence(),
                Event.Status.ACTIVE,
                incoming.isRecurring(),
                incoming.sourceEmailId(),
                incoming.sourceEmailSubject(),
                incoming.sourceEmailReceivedAt());
        eventRepository.save(updated);
    }

    private boolean createNewEvent(Event incoming) {
        if (incoming.status() == Event.Status.ACTIVE || incoming.status() == Event.Status.SCHEDULED) {
            String newId = (incoming.id() == null || incoming.id().isEmpty())
                    ? UUID.randomUUID().toString()
                    : incoming.id();

            Event newEvent = new Event(
                    newId,
                    incoming.title(),
                    incoming.startDate(),
                    incoming.endDate(),
                    incoming.allDay(),
                    incoming.notes(),
                    incoming.confidence(),
                    Event.Status.ACTIVE,
                    incoming.isRecurring(),
                    incoming.sourceEmailId(),
                    incoming.sourceEmailSubject(),
                    incoming.sourceEmailReceivedAt());
            eventRepository.save(newEvent);
            return true;
        }
        return false;
    }

    private boolean isSameEvent(Event e1, Event e2) {
        String t1 = normalize(e1.title());
        String t2 = normalize(e2.title());

        if (t1.equals(t2))
            return true;

        // Extract Year discriminators (Year 1, Yr 2, etc.)
        Integer y1 = extractYear(t1);
        Integer y2 = extractYear(t2);

        if (y1 != null && y2 != null && !y1.equals(y2))
            return false;

        // Assembly check
        boolean isAssembly1 = t1.contains("assembly") || t1.contains("gwasanaeth");
        boolean isAssembly2 = t2.contains("assembly") || t2.contains("gwasanaeth");
        if (isAssembly1 && isAssembly2) {
            boolean yearMatch = (y1 == null && y2 == null) || (y1 != null && y1.equals(y2));
            if (yearMatch)
                return true;
        }

        // Library check
        if ((t1.contains("library") || t1.contains("llyfrgell")) &&
                (t2.contains("library") || t2.contains("llyfrgell"))) {
            return true;
        }

        // PTA/Mother's/Easter Event check
        if (t1.contains("pta") && t2.contains("pta")) {
            if (t1.contains("mother") && t2.contains("mother"))
                return true;
            if (t1.contains("easter") && t2.contains("easter"))
                return true;
        }

        return calculateWordOverlap(t1, t2) > 0.7;
    }

    private String normalize(String s) {
        if (s == null)
            return "";
        return s.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Integer extractYear(String s) {
        // Matches "year 5", "yr 5", "y 5", "bl 5" (Welsh "Blwyddyn")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(year|yr|bl|blwyddyn|y)\\s*([1-6])").matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(2));
        }
        return null;
    }

    private double calculateWordOverlap(String s1, String s2) {
        String[] w1 = s1.split(" ");
        String[] w2 = s2.split(" ");
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(w1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(w2));

        if (set1.isEmpty() || set2.isEmpty())
            return 0;

        int intersection = 0;
        for (String w : set1) {
            if (set2.contains(w))
                intersection++;
        }

        return (double) intersection / Math.min(set1.size(), set2.size());
    }
}
