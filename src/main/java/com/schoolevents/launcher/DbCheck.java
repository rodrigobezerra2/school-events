package com.schoolevents.launcher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheck {
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlite:school_events.db";
        String messageId = "<CAMxkmMDvD8Q8TCqf3JuczC1EUrGWR8qJkXECyya2Ch2sFJKD8w@mail.gmail.com>";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt
                    .executeQuery("SELECT message_id FROM processed_emails WHERE message_id = '" + messageId + "'");

            if (rs.next()) {
                System.out.println("EMAIL_STATUS: PROCESSED");
            } else {
                System.out.println("EMAIL_STATUS: NOT_PROCESSED");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
