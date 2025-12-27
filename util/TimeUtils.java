package util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimeUtils {
    
    /**
     * Formats a timestamp as ISO 8601 string (UTC)
     */
    public static String formatAsIso8601(Timestamp timestamp) {
        if (timestamp == null) {
            return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }
    
    /**
     * Calculates processing time in milliseconds between two timestamps
     */
    public static long calculateProcessingTimeMs(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return 0;
        }
        return end.getTime() - start.getTime();
    }
}

