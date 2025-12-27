import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApiServer {
    private HttpServer server;
    private ContentSummarizerService service;
    private int port;
    
    public ApiServer(int port, ContentSummarizerService service) {
        this.port = port;
        this.service = service;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Register POST /submit endpoint
        server.createContext("/submit", new SubmitHandler());
        
        // Register GET /status/{jobId} endpoint
        server.createContext("/status/", new StatusHandler());
        
        // Register GET /result/{jobId} endpoint
        server.createContext("/result/", new ResultHandler());
        
        server.setExecutor(null); // Use default executor
        server.start();
        
        System.out.println("API Server started on port " + port);
        System.out.println("Submit endpoint: POST http://localhost:" + port + "/submit");
        System.out.println("Status endpoint: GET http://localhost:" + port + "/status/{jobId}");
        System.out.println("Result endpoint: GET http://localhost:" + port + "/result/{jobId}");
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("API Server stopped");
        }
    }
    
    private String extractJobId(String path, String prefix) {
        // Extract jobId from /status/{jobId} or /result/{jobId}
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            String prefixName = prefix.substring(1, prefix.length() - 1); // Remove leading / and trailing /
            if (parts[1].equals(prefixName)) {
                return parts[2];
            }
        }
        return null;
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] buffer = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
    
    private class SubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                
                // Parse JSON request body
                String userId = extractJsonValue(requestBody, "user_id");
                String content = extractJsonValue(requestBody, "content");
                String isUrlStr = extractJsonValue(requestBody, "is_url");
                
                if (userId == null || userId.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"user_id is required\"}", 400);
                    return;
                }
                
                if (content == null || content.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"content is required\"}", 400);
                    return;
                }
                
                boolean isUrl = "true".equalsIgnoreCase(isUrlStr) || "1".equals(isUrlStr);
                
                // Call service submit
                String jobId = service.submit(userId, content, isUrl);
                
                // Return jobId in JSON format
                String response = "{\n  \"job_id\": \"" + jobId + "\"\n}";
                sendResponse(exchange, response, 200);
                
            } catch (Exception e) {
                String errorResponse = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                sendResponse(exchange, errorResponse, 500);
            }
        }
    }
    
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String jobId = extractJobId(path, "/status/");
            
            if (jobId == null || jobId.isEmpty()) {
                sendResponse(exchange, "{\"error\": \"Job ID is required\"}", 400);
                return;
            }
            
            try {
                String response = service.getStatus(jobId);
                sendResponse(exchange, response, 200);
            } catch (Exception e) {
                String errorResponse = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                int statusCode = e.getMessage().contains("not found") ? 404 : 500;
                sendResponse(exchange, errorResponse, statusCode);
            }
        }
    }
    
    private class ResultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String jobId = extractJobId(path, "/result/");
            
            if (jobId == null || jobId.isEmpty()) {
                sendResponse(exchange, "{\"error\": \"Job ID is required\"}", 400);
                return;
            }
            
            try {
                String response = service.getResult(jobId);
                sendResponse(exchange, response, 200);
            } catch (Exception e) {
                String errorResponse = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                int statusCode = e.getMessage().contains("not found") ? 404 : 
                                e.getMessage().contains("not completed") ? 400 : 500;
                sendResponse(exchange, errorResponse, statusCode);
            }
        }
    }
    
    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private String extractJsonValue(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        
        // Simple JSON parsing - look for "key": "value" or "key": value
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        // Find the colon after the key
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        
        // Skip whitespace after colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        // Check if value is a string (starts with ")
        if (json.charAt(valueStart) == '"') {
            // String value - find the closing quote
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            
            // Handle escaped quotes
            while (valueEnd > valueStart + 1 && json.charAt(valueEnd - 1) == '\\') {
                valueEnd = json.indexOf("\"", valueEnd + 1);
                if (valueEnd == -1) return null;
            }
            
            String value = json.substring(valueStart + 1, valueEnd);
            // Unescape JSON string
            return unescapeJson(value);
        } else {
            // Non-string value (boolean, number) - find the end (comma, }, or whitespace)
            int valueEnd = valueStart;
            while (valueEnd < json.length() && 
                   json.charAt(valueEnd) != ',' && 
                   json.charAt(valueEnd) != '}' && 
                   json.charAt(valueEnd) != ']' &&
                   !Character.isWhitespace(json.charAt(valueEnd))) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }
    
    private String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\\"", "\"")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\\", "\\");
    }
}

