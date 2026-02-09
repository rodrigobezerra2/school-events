# Manual Event Extraction Wiki

This utility allows you to fetch a specific email from your inbox and test the AI extraction logic without affecting the main database.

## How it Works

The manual extraction utility:
1.  Connects to your Gmail inbox via IMAP.
2.  Searches for emails matching a specific **Subject**.
3.  Loads the email body and any PDF attachments.
4.  Sends the content to Gemini AI for event extraction.
5.  Prints a detailed report to the console.
6.  Saves unencrypted events to `debug/manual_extraction.json` for inspection.

## Prerequisite: Environment Setup

Ensure you have a `.env` file in the project root with the following:

```env
GMAIL_USERNAME="your-email@gmail.com"
GMAIL_PASSWORD="your-app-password"
GEMINI_API_KEY="your-gemini-api-key"
```

## Running a Manual Extraction

Run the following command in your terminal:

```bash
mvn exec:java -Dexec.mainClass="com.schoolevents.launcher.ManualTest" -Dexec.args="SUBJECT_OF_THE_EMAIL"
```

> [!TIP]
> If you don't provide an argument, it defaults to searching for the newsletter subject:  
> `Fwd: CYLCHLYTHYR - IONAWR 2026 / NEWSLETTER - JANUARY 2026`

## Inspecting Results

### 1. Console Report
After running, check the console for a section like this:
```text
==================================================
LOADED EMAIL REPORT
==================================================
Subject:     Fwd: CYLCHLYTHYR - IONAWR 2026...
Sender:      Rodrigo Bezerra <...>
Received:    2026-02-05T22:14:00
ID:          <...>
Body Size:   1234 chars
Attachments: 1
 - newsletter.pdf (application/pdf)

Requesting AI extraction...
SUCCESS: Extracted 3 events.
 - [2026-02-05] Parent Evening
 ...
```

### 2. Output File
The extracted events are saved as a plain JSON array in:
`debug/manual_extraction.json`

This file is excluded from Git to prevent accidental leakage of unencrypted event data.

## Force Rescan Feature

If you suspect an email was incorrectly processed or you want to "refresh" events from a specific date:

1.  **Open `.env`**.
2.  Set `FORCE_RESCAN=true`.
3.  (Optional) Set `RESCAN_SINCE=2026-02-01` to limit the scope.
4.  Run the application: `mvn exec:java -Dexec.mainClass="com.schoolevents.launcher.Main"`

> [!NOTE]
> The reconciliation service will attempt to update existing events rather than creating duplicates if the event details are similar.
