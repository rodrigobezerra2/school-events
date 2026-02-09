package com.schoolevents.domain.port.out;

import com.schoolevents.domain.model.Event;
import java.util.List;
import java.util.Optional;

public interface EventRepositoryPort {
    void save(Event event);

    List<Event> findAll();

    Optional<Event> findByTitleAndStartDate(String title, java.time.LocalDateTime startDate);

    List<Event> findByDate(java.time.LocalDateTime date);

    void delete(String id);
}
