# Async Content Summarizer

A Java-based asynchronous content summarization service that processes text or URL content and generates summaries using Hugging Face API(Free).

## Prerequisites

- **Java JDK 8+** (Java 11 or higher recommended)
- **MySQL 5.7+** or **MySQL 8.0+**
- **MySQL JDBC Driver** (`mysql-connector-java-8.2.0.jar` or compatible)
- **Lombok** (`lombok.jar`)
- **Hugging Face API Token** ( for summarization)

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

# Verify MySQL is running
mysql --version
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

### 3. Download Dependencies

The project requires two JAR files in the project root:

#### Option A: Manual Download

1. **MySQL JDBC Driver:**
   - Download from: https://dev.mysql.com/downloads/connector/j/
   - Place `mysql-connector-java-8.2.0.jar` in the project root

2. **Lombok:**
   - Download from: https://projectlombok.org/download
   - Place `lombok.jar` in the project root

#### Option B: Using Maven (if you have Maven installed)

The `run.sh` script will automatically look for these JARs in your Maven repository (`~/.m2/repository/`).

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

