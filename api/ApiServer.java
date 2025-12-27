package api;

import service.ContentSummarizerService;
import util.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApiServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int BUFFER_SIZE = 8192;
    
    private HttpServer server;
    private ContentSummarizerService service;
    private int port;
    
    public ApiServer(int port, ContentSummarizerService service) {
        this.port = port;
        this.service = service;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/users", new UserHandler());
        server.createContext("/submit", new SubmitHandler());
        server.createContext("/status/", new StatusHandler());
        server.createContext("/result/", new ResultHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("API Server started on port " + port);
        System.out.println("Create user endpoint: POST http://localhost:" + port + "/users");
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
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            String prefixName = prefix.substring(1, prefix.length() - 1);
            if (parts[1].equals(prefixName)) {
                return parts[2];
            }
        }
        return null;
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] buffer = new byte[BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
    
    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private int determineStatusCode(Exception e) {
        String message = e.getMessage().toLowerCase();
        if (message.contains("not found")) return 404;
        if (message.contains("not completed")) return 400;
        if (message.contains("already exists")) return 409;
        if (message.contains("foreign key") || message.contains("constraint")) return 400;
        return 500;
    }
    
    private class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                String userId = JsonUtils.extractValue(requestBody, "user_id");
                String name = JsonUtils.extractValue(requestBody, "name");
                String userTypeStr = JsonUtils.extractValue(requestBody, "user_type");
                
                if (userId == null || userId.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"user_id is required\"}", 400);
                    return;
                }
                
                if (name == null || name.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"name is required\"}", 400);
                    return;
                }
                
                // Default to CLIENT if user_type not provided
                enums.UserType userType = enums.UserType.CLIENT;
                if (userTypeStr != null && !userTypeStr.isEmpty()) {
                    try {
                        userType = enums.UserType.valueOf(userTypeStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        sendResponse(exchange, "{\"error\": \"Invalid user_type. Must be CLIENT or ADMIN\"}", 400);
                        return;
                    }
                }
                
                service.createUser(userId, name, userType);
                String response = "{\n  \"message\": \"User created successfully\",\n  \"user_id\": \"" + userId + "\"\n}";
                sendResponse(exchange, response, 201);
                
            } catch (Exception e) {
                String errorResponse = "{\"error\": \"" + JsonUtils.escape(e.getMessage()) + "\"}";
                sendResponse(exchange, errorResponse, determineStatusCode(e));
            }
        }
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
                String userId = JsonUtils.extractValue(requestBody, "user_id");
                String content = JsonUtils.extractValue(requestBody, "content");
                String isUrlStr = JsonUtils.extractValue(requestBody, "is_url");
                
                if (userId == null || userId.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"user_id is required\"}", 400);
                    return;
                }
                
                if (content == null || content.isEmpty()) {
                    sendResponse(exchange, "{\"error\": \"content is required\"}", 400);
                    return;
                }
                
                // Parse is_url: default to false if not provided, only true if explicitly "true" or "1"
                boolean isUrl = false;
                if (isUrlStr != null && !isUrlStr.isEmpty()) {
                    isUrl = "true".equalsIgnoreCase(isUrlStr.trim()) || "1".equals(isUrlStr.trim());
                }
                String jobId = service.submit(userId, content, isUrl);
                String response = "{\n  \"job_id\": \"" + jobId + "\"\n}";
                sendResponse(exchange, response, 200);
                
            } catch (Exception e) {
                String errorResponse = "{\"error\": \"" + JsonUtils.escape(e.getMessage()) + "\"}";
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
                String errorResponse = "{\"error\": \"" + JsonUtils.escape(e.getMessage()) + "\"}";
                sendResponse(exchange, errorResponse, determineStatusCode(e));
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
                String errorResponse = "{\"error\": \"" + JsonUtils.escape(e.getMessage()) + "\"}";
                sendResponse(exchange, errorResponse, determineStatusCode(e));
            }
        }
    }
}

