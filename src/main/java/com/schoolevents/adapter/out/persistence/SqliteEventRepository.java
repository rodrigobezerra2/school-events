package com.schoolevents.adapter.out.persistence;

import com.schoolevents.domain.model.Event;
import com.schoolevents.domain.port.out.EventRepositoryPort;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteEventRepository implements EventRepositoryPort {

    private final String dbUrl;

    public SqliteEventRepository(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public void save(Event event) {
        String sql = "INSERT INTO events (id, title, start_date, end_date, all_day, notes, confidence, status, source_email_id, source_email_subject, source_email_received_at, is_recurring) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "title=excluded.title, start_date=excluded.start_date, end_date=excluded.end_date, " +
                "all_day=excluded.all_day, notes=excluded.notes, confidence=excluded.confidence, " +
                "status=excluded.status, source_email_id=excluded.source_email_id, " +
                "source_email_subject=excluded.source_email_subject, source_email_received_at=excluded.source_email_received_at, "
                +
                "is_recurring=excluded.is_recurring";

        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, event.id());
            pstmt.setString(2, event.title());
            pstmt.setString(3, event.startDate().toString());
            pstmt.setString(4, event.endDate() != null ? event.endDate().toString() : null);
            pstmt.setInt(5, event.allDay() ? 1 : 0);
            pstmt.setString(6, event.notes());
            pstmt.setDouble(7, event.confidence());
            pstmt.setString(8, event.status().name());
            pstmt.setString(9, event.sourceEmailId());
            pstmt.setString(10, event.sourceEmailSubject());
            pstmt.setString(11,
                    event.sourceEmailReceivedAt() != null ? event.sourceEmailReceivedAt().toString() : null);
            pstmt.setInt(12, event.isRecurring() ? 1 : 0);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save event", e);
        }
    }

    @Override
    public List<Event> findAll() {
        String sql = "SELECT * FROM events ORDER BY start_date ASC";
        List<Event> events = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                events.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load events", e);
        }
        return events;
    }

    @Override
    public Optional<Event> findByTitleAndStartDate(String title, LocalDateTime startDate) {
        String sql = "SELECT * FROM events WHERE title = ? AND start_date = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, title);
            pstmt.setString(2, startDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find event", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Event> findByDate(LocalDateTime date) {
        String datePrefix = date.toLocalDate().toString() + "%";
        String sql = "SELECT * FROM events WHERE start_date LIKE ?";
        List<Event> events = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, datePrefix);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find events by date", e);
        }
        return events;
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete event", e);
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        return new Event(
                rs.getString("id"),
                rs.getString("title"),
                LocalDateTime.parse(rs.getString("start_date")),
                rs.getString("end_date") != null ? LocalDateTime.parse(rs.getString("end_date")) : null,
                rs.getInt("all_day") == 1,
                rs.getString("notes"),
                rs.getDouble("confidence"),
                Event.Status.valueOf(rs.getString("status")),
                rs.getInt("is_recurring") == 1,
                rs.getString("source_email_id"),
                rs.getString("source_email_subject"),
                rs.getString("source_email_received_at") != null
                        ? LocalDateTime.parse(rs.getString("source_email_received_at"))
                        : null);
    }
}
