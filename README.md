# School Events Organizer

A local Java backend application that automatically connects to a Gmail inbox, extracts school event details using Google Gemini AI, and exports them as a JSON file for consumption by a frontend.

## Architecture

This project follows **Hexagonal Architecture (Ports & Adapters)** to decouple the core domain logic from external dependencies (Gmail, SQLite, Gemini API).

### Diagram
```
[ CLI (Main) ] -> [ ProcessInboxUseCase ]
                        |
      +-----------------+-----------------+
      |                 |                 |
[ GmailAdapter ]  [ GeminiAiAdapter ] [ EventReconciliation ]
      |                 |                 |
   (IMAP)            (HTTP)        [ EventRepository ]
                                          |
                                     (SQLite JDBC)
```

### Module Structure
- **domain**: Core business logic (`Event`, `ReconciliationService`) and Ports interfaces. Dependencies: None.
- **application**: Orchestration logic (`ProcessInboxUseCase`). Dependencies: Domain.
- **adapter**: Implementations of Ports. Dependencies: Application, Domain, External libs.
    - **in/cli**: Application entry point.
    - **out/email**: Connects to Gmail.
    - **out/ai**: Connects to Gemini API.
    - **out/persistence**: SQLite storage.
    - **out/filesystem**: JSON export.

## Prerequisites

- Java 21+
- Maven
- A Gmail account with 2-Step Verification enabled.
- Google Gemini API Key.

## Setup

### 1. Gmail Configuration
1.  Go to your Google Account settings -> Security.
2.  Enable **2-Step Verification**.
3.  Search for **App Passwords**.
4.  Create a new App Password (name it "School Events").
5.  Copy the 16-character password. **You will need to paste this into the `GMAIL_PASSWORD` variable in your `.env` file (see section 3 below).**

#### Best Practices (Important)
Since this application reads your inbox to find events, we **strongly recommend** using a dedicated Gmail account rather than your personal primary email.

**Recommended Setup:**
1.  Create a new Gmail account (e.g., `my-school-bot@gmail.com`).
2.  In your **Main Personal Email**, set up a Filter:
    - **Criteria**: From specific school addresses (e.g., `principal@school.edu`, `newsletter@district.org`).
    - **Action**: Forward to `my-school-bot@gmail.com`.
3.  Configure this application to use the `my-school-bot` credentials.

This ensures the AI only sees relevant emails and keeps your personal inbox private.

### 2. Gemini API Key
1.  Visit [Google AI Studio](https://makersuite.google.com/app/apikey).
2.  Create an API Key.

### 3. Application Configuration
Copy `.env.example` to environment variables or set them in your IDE/Shell:
```bash
export GMAIL_USERNAME="your-email@gmail.com"
export GMAIL_PASSWORD="xxxx xxxx xxxx xxxx"
export GEMINI_API_KEY="your-key"
export AI_ENABLED=true
export DB_URL="jdbc:sqlite:school_events.db"
```

### 3. Cloud Storage & Encryption (Optional)
To upload encrypted events to Google Drive for online viewing:

1. **Google Cloud Setup**:
    - Create a Service Account in [Google Cloud Console](https://console.cloud.google.com/).
    - Enable "Google Drive API".
    - Download the JSON key file (`credentials.json`).
    - Create a folder in Google Drive.
    - **Share** that folder with the Service Account's email address (found in the JSON file).

2. **Configuration**:
    - Set `GOOGLE_CREDENTIALS_JSON` to the absolute path of your key file.
    - Set `DRIVE_FOLDER_ID` to the ID of the shared folder (from the URL).
    - Set `UI_PASSWORD` to a secret password. The uploaded file will be encrypted with this.

**Note**: If these variables are not set, the application will only save `events.json` locally and skip the upload.

## Running Locally

### 1. Configure
Create a `.env` file in the root directory (copy from `.env.example`). 

> [!CAUTION]
> Never commit your `.env` file or include real credentials in `.env.example`. Ensure `.env` is listed in your `.gitignore`.

```bash
GMAIL_USERNAME="your-email@gmail.com"
GMAIL_PASSWORD="xxxx xxxx xxxx xxxx"
GEMINI_API_KEY="your-key"
```

### 2. Run
Since configuration is loaded from `.env`, you can run the application with a single command:

```bash
# Via Maven
mvn exec:java -Dexec.mainClass="com.schoolevents.launcher.Main"

# Or via JAR (after building with 'mvn package')
java -jar target/school-events-organizer-1.0.0-SNAPSHOT.jar
```

### 3. Manual Extraction Test
To test extraction on a specific email and generate a debug report:
```bash
mvn exec:java -Dexec.mainClass="com.schoolevents.launcher.ManualTest" -Dexec.args="SUBJECT_OF_EMAIL"
```
See [WIKI.md](file:///c:/Users/User/Development/projects/school%20events%20organizer/WIKI.md) for more details.

## Testing

Run unit and architecture tests:
```bash
mvn test
```

## Scheduling (Cron)

To run this weekly (e.g., every Friday at 6 PM):
```bash
0 18 * * 5 /usr/bin/java -jar /path/to/school-events-organizer.jar >> /var/log/school-events.log 2>&1
```

## Docker

### Build Image
```bash
docker build -t school-events-organizer .
```

### Run Container
To run with persistence (saving the DB) and valid credentials, you need to mount volumes and pass environment variables.

```bash
docker run --rm -it \
  --env-file .env \
  -v $(pwd)/school_events.db:/app/school_events.db \
  -v $(pwd)/output:/app/output \
  -v /path/to/your/credentials.json:/app/credentials.json \
  -e GOOGLE_CREDENTIALS_JSON="/app/credentials.json" \
  school-events-organizer
```
*Note: Ensure `drive_credentials.json` or whatever you named it is mapped correctly to the path set in the `GOOGLE_CREDENTIALS_JSON` env var.*

## Frontend UI

The project includes a modern, password-protected web interface to view your events.

### Setup & Run
1.  Navigate to the UI directory:
    ```bash
    cd ui
    ```
2.  Install dependencies:
    ```bash
    npm install
    ```
3.  Start the development server:
    ```bash
    npm run dev
    ```
4.  Open the URL shown (usually `http://localhost:5173`).

### Loading Data
For this local version, the UI expects `events.json` to be served.
- **Option A (Simulated)**: Copy `../output/events.json` to `ui/public/events.json`.
- **Option B (Real World)**: In a production app, the UI would fetch from the Google Drive API directly using the user's login. For this demo, manual file placement is the simplest verification method.

### Unlocking
Enter the `UI_PASSWORD` you set in your `.env` file to decrypt and view the calendar.

## Troubleshooting

- **Authentication Failed**: Check your Gmail App Password.
- **No Events Extracted**: Check if `AI_ENABLED` is true and if emails contain clear date info.
- **Database Locked**: Ensure no other process is holding the SQLite file lock.

## Persistence
Data is stored in `school_events.db` (SQLite).
- `events`: Extracted event data.
- `processed_emails`: Tracks processed message IDs to avoid duplicates.
