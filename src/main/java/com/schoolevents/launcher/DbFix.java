package com.schoolevents.launcher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbFix {
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlite:school_events.db";
        String messageId = "<CAMxkmMDvD8Q8TCqf3JuczC1EUrGWR8qJkXECyya2Ch2sFJKD8w@mail.gmail.com>";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement stmt = conn.createStatement();
            int deleted = stmt.executeUpdate("DELETE FROM processed_emails WHERE email_id = '" + messageId + "'");
            System.out.println("RECORDS_DELETED: " + deleted);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
