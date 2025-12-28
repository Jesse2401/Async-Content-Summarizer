# Async Content Summarizer

A Java-based asynchronous content summarization service that processes text or URL content and generates summaries using Hugging Face API(Free).

## Prerequisites

- **Java JDK 8+** (Java 11 or higher recommended)
- **Maven** (for dependency management)
- **MySQL 5.7+** or **MySQL 8.0+**
- **Hugging Face API Token** (for summarization)

## Quick Setup

### 1. Install MySQL

Make sure MySQL is installed and running on your system:

```bash
# macOS (using Homebrew)
brew install mysql
brew services start mysql

# Linux (Ubuntu/Debian)
sudo apt-get install mysql-server
sudo systemctl start mysql

# Verify MySQL installation and status
mysql --version
# Check if MySQL is running (macOS)
brew services list | grep mysql
# Check if MySQL is running (Linux)
sudo systemctl status mysql
```

### 2. Create Database Configuration

Create a `.env` file in the project root directory:

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=asyncContentSummariser
DB_USER=root
DB_PASSWORD=your_mysql_password

# Hugging Face API Token
HUGGING_FACE_TOKEN=your_hugging_face_token
```

**Note:** Replace `your_mysql_password` with your actual MySQL root password, and `your_hugging_face_token` with your Hugging Face API token if you have one.

### 3. Install Maven

Maven is required to automatically download dependencies. Install Maven using one of the following methods:

```bash
# macOS (using Homebrew)
brew install maven

# Linux (Ubuntu/Debian)
sudo apt-get install maven

# Verify Maven installation
mvn --version
```

The `run.sh` script will automatically look for required JARs (MySQL JDBC Driver and Lombok) in your Maven repository (`~/.m2/repository/`). If they're not found, Maven will download them automatically when you first run the application.

### 4. Run the Application

#### Using the provided script (Recommended):

```bash
chmod +x run.sh
./run.sh
```

The script will:
- Check for required JAR files
- Compile the Java source files
- Start the application on port 8080

#### Manual compilation and run:

```bash
# Compile
javac -cp ".:mysql-connector-java-8.2.0.jar:lombok.jar" \
  -processor lombok.launch.AnnotationProcessorHider\$AnnotationProcessor \
  config/*.java enums/*.java models/*.java strategy/*.java \
  dao/*.java worker/*.java service/*.java api/*.java util/*.java \
  Application.java

# Run
java -cp ".:mysql-connector-java-8.2.0.jar:lombok.jar:config:dao:worker:strategy:enums:models:service:api:util" \
  Application
```

### 5. Verify Installation

Once the application starts, you should see:

```
Initializing database...
Database 'asyncContentSummariser' created successfully!
Table 'users' created successfully!
Table 'jobs' created successfully!
API Server started on port 8080
Application started successfully!
API Server running on http://localhost:8080
```

## API Endpoints

The service exposes the following REST endpoints:

- **Create User:** `POST http://localhost:8080/users`
  ```json
  {
    "user_id": "user123",
    "name": "John Doe",
    "user_type": "CLIENT"
  }
  ```

- **Submit Job:** `POST http://localhost:8080/submit`
  ```json
  {
    "user_id": "user123",
    "content": "Your text/url content here",
    "is_url": false
  }
  ```

- **Check Status:** `GET http://localhost:8080/status/{jobId}`

- **Get Result:** `GET http://localhost:8080/result/{jobId}`

## Architecture Overview

The application follows a layered architecture with asynchronous job processing:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (HTTP Requests)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Layer (ApiServer)                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ /users   │  │ /submit  │  │ /status  │  │ /result  │         │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Service Layer (ContentSummarizerService)            │
│  • User Management                                               │
│  • Job Submission & Status                                      │
│  • Cache Lookup (RedisCache)                                    │
└────────────┬───────────────────────────────┬────────────────────┘
             │                               │
             ▼                               ▼
┌──────────────────────────┐    ┌──────────────────────────────┐
│   Queue Service          │    │   DAO Layer                   │
│   (In-Memory Queue)      │    │   • JobDao                    │
│   • Enqueue jobs         │    │   • UserDao                   │
│   • Dequeue jobs         │    │                               │
└────────────┬─────────────┘    └───────────────┬────────────────┘
             │                                   │
             │                                   ▼
             │                    ┌──────────────────────────────┐
             │                    │   MySQL Database             │
             │                    │   • users table              │
             │                    │   • jobs table               │
             │                    └──────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Worker Thread (JobWorker)                           │
│  • Polls queue for jobs                                         │
│  • Checks cache before processing                               │
│  • Fetches URL content (if needed)                             │
│  • Calls HuggingFaceStrategy for summarization as of now       │
│  • Stores result in DB and cache                                │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Strategy Layer (HuggingFaceStrategy)                  │
│  • Generates summary using Hugging Face API                      │
│  • Handles multiple model fallbacks                              │
│  • Extracts and normalizes response                              │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│              External Services                                    │
│  • Hugging Face API (Summarization)                             │
│  • URL Content Fetching (HTTP)                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              In-Memory Cache (RedisCache)                        │
│  • Stores summaries by content hash                             │
│  • Tracks processing jobs                                        │
│  • Singleton pattern                                             │
└─────────────────────────────────────────────────────────────────┘
```

### Architecture Components:

1. **API Layer** (`api/`): 
   - Handles HTTP requests/responses
   - RESTful endpoints for user management and job operations
   - Built on Java's `HttpServer`

2. **Service Layer** (`service/`):
   - Business logic orchestration
   - Cache lookup before processing
   - Job creation and status management

3. **DAO Layer** (`dao/`):
   - Database abstraction
   - CRUD operations for Users and Jobs
   - Connection management

4. **Worker Thread** (`worker/JobWorker`):
   - Background job processor
   - Polls queue for pending jobs
   - Handles URL content extraction
   - Coordinates with strategy for summarization

5. **Queue Service** (`worker/QueueService`):
   - In-memory job queue (BlockingQueue)
   - Decouples job submission from processing
   - Thread-safe job distribution

6. **Cache Layer** (`worker/RedisCache`):
   - In-memory cache (ConcurrentHashMap)
   - Stores summaries by content hash
   - Prevents duplicate processing

7. **Strategy Pattern** (`strategy/`):
   - Pluggable summarization strategies
   - Currently implements HuggingFaceStrategy
   - Easy to extend with other providers

8. **Database** (MySQL):
   - Persistent storage for users and jobs
   - Auto-initialized on startup
   - Tracks job status and results

### Request Flow:

1. **Submit Job**: Client → API → Service → Cache Check → Queue/DB
2. **Process Job**: Worker → Queue → Cache Check → Strategy → DB/Cache
3. **Get Result**: Client → API → Service → DAO → Database

### Key Features:

- **Asynchronous Processing**: Jobs are queued and processed in background
- **Caching**: Prevents duplicate API calls for same content
- **Deduplication**: Multiple requests for same content share processing
- **Scalable**: Worker thread can be extended to multiple workers

## Caching Behavior

The application uses an **in-memory cache** to store summaries of processed content:

- **Cache Hit**: If you submit the same content twice (while the application is running), the second submission will immediately return the cached summary without processing.
- **Cache Persistence**: The cache is stored in memory only and is **lost when the application restarts**. After restart, previously processed content will be processed again.
- **Cache Key**: Content is identified by an MD5 hash of the input text/URL, so identical content always maps to the same cache key.

## Project Structure

```
asyncContentSummarizer/
├── api/              # REST API server
├── config/           # Database configuration
├── dao/              # Data access objects
├── enums/            # Enumerations
├── models/           # user and job Data models
├── service/          # Business logic
├── strategy/         # Summarization strategies
├── util/             # Utility classes
├── worker/           # Background job processing(job,queue and redis worker)
├── Application.java   # Main entry point
└── run.sh            # Build and run script
```

