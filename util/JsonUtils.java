package util;

public class JsonUtils {
    
    /**
     * Escapes special characters in a string for JSON encoding
     */
    public static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Unescapes JSON string values
     */
    public static String unescape(String input) {
        if (input == null) return "";
        return input.replace("\\\"", "\"")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\\", "\\");
    }
    
    /**
     * Extracts a value from a JSON string by key
     */
    public static String extractValue(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            
            while (valueEnd > valueStart + 1 && json.charAt(valueEnd - 1) == '\\') {
                valueEnd = json.indexOf("\"", valueEnd + 1);
                if (valueEnd == -1) return null;
            }
            
            String value = json.substring(valueStart + 1, valueEnd);
            return unescape(value);
        } else {
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
}

