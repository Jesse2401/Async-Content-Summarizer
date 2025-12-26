import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class DatabaseConfiguration {
    // Environment variables cache
    private static Map<String, String> envMap = new HashMap<>();
    private static boolean envLoaded = false;
    
    // Database connection
    private static Connection connection = null;
    
    // Database configuration from .env
    private static String DB_HOST;
    private static String DB_PORT;
    private static String DB_NAME;
    private static String DB_USER;
    private static String DB_PASSWORD;
    
    static {
        // Load MySQL JDBC driver (optional - modern JDBC can auto-detect)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Warning: MySQL JDBC Driver not found at class load time.");
            System.err.println("Driver will be auto-detected if present in classpath at runtime.");
        }
        
        // Load environment variables
        loadEnv();
        
        // Initialize database configuration
        DB_HOST = getEnv("DB_HOST", "localhost");
        DB_PORT = getEnv("DB_PORT", "3306");
        DB_NAME = getEnv("DB_NAME", "asyncContentSummariser");
        DB_USER = getEnv("DB_USER", "root");
        DB_PASSWORD = getEnv("DB_PASSWORD", "");
    }
    
    /**
     * Load environment variables from .env file
     */
    private static void loadEnv() {
        if (envLoaded) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) || 
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envMap.put(key, value);
                }
            }
            envLoaded = true;
        } catch (IOException e) {
            System.err.println("Warning: .env file not found. Using default values.");
        }
    }
    
    /**
     * Get environment variable (checks .env file, then system env, then default)
     */
    private static String getEnv(String key, String defaultValue) {
        loadEnv();
        
        if (envMap.containsKey(key) && !envMap.get(key).isEmpty()) {
            return envMap.get(key);
        }
        
        String systemEnv = System.getenv(key);
        if (systemEnv != null && !systemEnv.isEmpty()) {
            return systemEnv;
        }
        
        return defaultValue;
    }
    
    /**
     * Get database connection
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String dbUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            connection = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
        }
        return connection;
    }
    
    /**
     * Get connection without database (for creating database)
     */
    private static Connection getConnectionWithoutDb() throws SQLException {
        String baseUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT;
        return DriverManager.getConnection(baseUrl, DB_USER, DB_PASSWORD);
    }
    
    /**
     * Close database connection
     */
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    /**
     * Initialize database and tables
     */
    public static void initialize() {
        try {
            createDatabaseIfNotExists();
            createTablesIfNotExists();
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create database if it doesn't exist
     */
    private static void createDatabaseIfNotExists() throws SQLException {
        Connection conn = getConnectionWithoutDb();
        try (Statement stmt = conn.createStatement()) {
            // Check if database exists
            var resultSet = stmt.executeQuery("SHOW DATABASES LIKE '" + DB_NAME + "'");
            boolean exists = resultSet.next();
            resultSet.close();
            
            if (!exists) {
                stmt.executeUpdate("CREATE DATABASE " + DB_NAME);
                System.out.println("Database '" + DB_NAME + "' created successfully!");
            }
        } finally {
            conn.close();
        }
    }
    
    /**
     * Create tables if they don't exist
     */
    private static void createTablesIfNotExists() throws SQLException {
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            // Check and create Users table
            var resultSet = stmt.executeQuery("SHOW TABLES LIKE 'users'");
            boolean usersExists = resultSet.next();
            resultSet.close();
            
            if (!usersExists) {
                String createUsersTable = "CREATE TABLE users (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "name VARCHAR(255) NOT NULL, " +
                        "jobIDs TEXT, " +
                        "userType VARCHAR(20) NOT NULL, " +
                        "createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                        ")";
                stmt.executeUpdate(createUsersTable);
                System.out.println("Table 'users' created successfully!");
            }
            
            // Check and create Jobs table
            resultSet = stmt.executeQuery("SHOW TABLES LIKE 'jobs'");
            boolean jobsExists = resultSet.next();
            resultSet.close();
            
            if (!jobsExists) {
                String createJobsTable = "CREATE TABLE jobs (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "userId VARCHAR(255) NOT NULL, " +
                        "inputContent TEXT NOT NULL, " +
                        "isUrl BOOLEAN NOT NULL DEFAULT FALSE, " +
                        "outputContent TEXT, " +
                        "status VARCHAR(20) NOT NULL DEFAULT 'QUEUED', " +
                        "createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE" +
                        ")";
                stmt.executeUpdate(createJobsTable);
                System.out.println("Table 'jobs' created successfully!");
            }
        }
    }
}

