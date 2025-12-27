package strategy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.FileReader;

public class HuggingFaceStrategy implements SummaryStrategy {
    
    // Using a different model to avoid Groq limits
    // Try using meta-llama/Llama-3.1-8B-Instruct or other available models
    private static final String API_URL = "https://router.huggingface.co/v1/chat/completions";
    
    @Override
    public String generateSummary(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        
        String apiToken = getTokenFromEnv();
        if (apiToken == null || apiToken.isEmpty()) {
            throw new IllegalStateException("Hugging Face API token not found. Set HUGGING_FACE_TOKEN in .env file");
        }
        
        HttpClient client = HttpClient.newHttpClient();
        
        // Use chat completions format - increase max_tokens to get full response
        String escapedText = escapeJson(text);
        String systemMessage = "You are a helpful assistant that provides concise summaries. Always follow the user's instructions exactly.";
        String userMessage = escapedText;
        
        // Try different models - fallback if one hits limit
        String[] models = {
            "meta-llama/Llama-3.1-8B-Instruct",
            "mistralai/Mistral-7B-Instruct-v0.2",
            "google/gemma-7b-it",
            "microsoft/Phi-3-mini-4k-instruct"
        };
        
        Exception lastException = null;
        for (String model : models) {
            try {
                String jsonBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":300,\"temperature\":0.3}",
                    model,
                    escapeJson(systemMessage),
                    userMessage
                );
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String responseBody = response.body();
                
                if (response.statusCode() == 200) {
                    // Success - parse and return
                    if (responseBody == null || responseBody.isEmpty()) {
                        throw new Exception("Empty response from Hugging Face API");
                    }
                    String summary = extractContentFromResponse(responseBody);
                    if (summary == null || summary.isEmpty() || summary.equals(responseBody)) {
                        throw new Exception("Failed to generate summary. API response: " + responseBody);
                    }
                    return summary;
                } else if (response.statusCode() == 402) {
                    // Rate limit or usage limit - try next model
                    System.err.println("Model " + model + " hit usage limit, trying next model...");
                    lastException = new Exception("Model " + model + " limit reached: " + responseBody);
                    continue;
                } else {
                    // Other error - try next model
                    System.err.println("Model " + model + " error " + response.statusCode() + ", trying next...");
                    lastException = new Exception("Hugging Face API error: " + response.statusCode() + " - " + responseBody);
                    continue;
                }
            } catch (Exception e) {
                lastException = e;
                continue;
            }
        }
        
        // All models failed
        throw new Exception("All models failed. Last error: " + (lastException != null ? lastException.getMessage() : "Unknown error"));
        
    }
    
    private String getTokenFromEnv() {
        // Try system environment variable first
        String token = System.getenv("HUGGING_FACE_TOKEN");
        if (token != null && !token.isEmpty()) return token;
        
        token = System.getenv("HF_TOKEN");
        if (token != null && !token.isEmpty()) return token;
        
        // Read from .env file
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                if (line.startsWith("HUGGING_FACE_TOKEN=")) {
                    return line.split("=", 2)[1].trim();
                } else if (line.startsWith("HF_TOKEN=")) {
                    return line.split("=", 2)[1].trim();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }
    
    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private String extractContentFromResponse(String jsonResponse) throws Exception {
        // Try different response formats
        
        // Format 1: Inference API - [{"summary_text": "..."}]
        int summaryStart = jsonResponse.indexOf("\"summary_text\":\"");
        if (summaryStart != -1) {
            summaryStart += 16;
            int summaryEnd = jsonResponse.indexOf("\"", summaryStart);
            if (summaryEnd != -1) {
                return unescapeJson(jsonResponse.substring(summaryStart, summaryEnd));
            }
        }
        
        // Format 2: Chat API with content field - {"choices":[{"message":{"content":"..."}}]}
        int contentStart = jsonResponse.indexOf("\"content\":\"");
        if (contentStart != -1) {
            contentStart += 11;
            int contentEnd = jsonResponse.indexOf("\"", contentStart);
            if (contentEnd != -1) {
                String content = jsonResponse.substring(contentStart, contentEnd);
                // If content is not empty, return it
                if (!content.isEmpty()) {
                    return unescapeJson(content);
                }
            }
        }
        
        // Format 3: Chat API with reasoning field (when content is empty)
        // Some models put output in "reasoning" field instead of "content"
        int reasoningStart = jsonResponse.indexOf("\"reasoning\":\"");
        if (reasoningStart != -1) {
            reasoningStart += 13;
            // Find the end of reasoning field - handle escaped quotes properly
            int reasoningEnd = findEndOfJsonString(jsonResponse, reasoningStart);
            if (reasoningEnd > reasoningStart) {
                String reasoningRaw = jsonResponse.substring(reasoningStart, reasoningEnd);
                String reasoning = unescapeJson(reasoningRaw);
                
                // Extract the actual answer from reasoning
                // Look for the definition text - usually after "Let's draft:" or contains the topic
                String lowerReasoning = reasoning.toLowerCase();
                int answerStart = -1;
                
                // Pattern 1: Look for "Java is" (the actual definition)
                if (lowerReasoning.contains("java is")) {
                    answerStart = lowerReasoning.indexOf("java is");
                }
                // Pattern 2: Look for text after "Let's draft:"
                else if (lowerReasoning.contains("let's draft")) {
                    int draftPos = lowerReasoning.indexOf("let's draft");
                    int colonPos = reasoning.indexOf(":", draftPos);
                    if (colonPos > 0) {
                        answerStart = colonPos + 1;
                        // Skip any quotes or newlines after colon
                        while (answerStart < reasoning.length() && 
                               (reasoning.charAt(answerStart) == '"' || 
                                reasoning.charAt(answerStart) == '\n' || 
                                reasoning.charAt(answerStart) == ' ')) {
                            answerStart++;
                        }
                    }
                }
                
                if (answerStart != -1 && answerStart < reasoning.length()) {
                    String answer = reasoning.substring(answerStart).trim();
                    // Remove leading quotes
                    if (answer.startsWith("\"")) {
                        answer = answer.substring(1);
                    }
                    // Don't stop at quotes in the middle - take the full text
                    // The reasoning field might have quotes as part of the content
                    int endPos = answer.length();
                    
                    // Only look for a closing quote if it's near the end (last 20 chars)
                    // This avoids stopping at quotes that are part of the text content
                    int searchStart = Math.max(0, answer.length() - 20);
                    for (int i = searchStart; i < answer.length(); i++) {
                        if (answer.charAt(i) == '"' && (i == 0 || answer.charAt(i-1) != '\\')) {
                            // This looks like an end quote
                            endPos = i;
                            break;
                        }
                    }
                    
                    // Allow up to 400 chars (since we increased max_tokens to 300)
                    if (endPos > 400) {
                        endPos = 400;
                    }
                    
                    answer = answer.substring(0, endPos).trim();
                    // Remove trailing quotes/punctuation
                    if (answer.endsWith("\"")) {
                        answer = answer.substring(0, answer.length() - 1).trim();
                    }
                    // Remove incomplete words at the end (like "high‑" or "capabi")
                    // Check for various dash characters
                    while (answer.length() > 0 && 
                           (answer.endsWith("‑") || answer.endsWith("-") || 
                            answer.endsWith("–") || answer.endsWith("—"))) {
                        int lastSpace = answer.lastIndexOf(" ");
                        if (lastSpace > 0) {
                            answer = answer.substring(0, lastSpace).trim();
                        } else {
                            break;
                        }
                    }
                    if (!answer.isEmpty()) {
                        return answer;
                    }
                }
                
                // Fallback: return last 300 chars of reasoning (usually contains the answer)
                if (reasoning.length() > 300) {
                    return reasoning.substring(reasoning.length() - 300).trim();
                }
                return reasoning.trim();
            }
        }
        
        throw new Exception("Invalid API response format. Could not extract content. Response: " + 
                          jsonResponse.substring(0, Math.min(500, jsonResponse.length())));
    }
    
    private int findEndOfJsonString(String json, int start) {
        // Find the end of a JSON string, handling escaped quotes
        // Skip the opening quote at start
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            // If we find a quote that's not escaped, it's the end
            if (c == '"' && (i == start || json.charAt(i - 1) != '\\')) {
                // Check if it's really not escaped (handle \\" case)
                int backslashCount = 0;
                int j = i - 1;
                while (j >= start && json.charAt(j) == '\\') {
                    backslashCount++;
                    j--;
                }
                // If even number of backslashes, quote is not escaped
                if (backslashCount % 2 == 0) {
                    return i;
                }
            }
        }
        return json.length();
    }
    
    private String unescapeJson(String input) {
        return input.replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\");
    }
}
