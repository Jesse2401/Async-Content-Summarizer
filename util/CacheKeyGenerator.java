package util;

import java.security.MessageDigest;

public class CacheKeyGenerator {
    private static final String CACHE_PREFIX = "content:";
    private static final String URL_PREFIX = "url:";
    private static final String TEXT_PREFIX = "text:";
    
    /**
     * Generates a cache key for content based on the input text and whether it's a URL
     */
    public static String generate(String text, boolean isUrl) {
        try {
            String input = (isUrl ? URL_PREFIX : TEXT_PREFIX) + text;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return CACHE_PREFIX + sb.toString();
        } catch (Exception e) {
            // Fallback to simple key
            return CACHE_PREFIX + (isUrl ? URL_PREFIX : TEXT_PREFIX) + text.hashCode();
        }
    }
}

