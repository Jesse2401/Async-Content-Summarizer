package worker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class RedisCache {
    private static RedisCache instance;
    private Map<String, String> cache;
    private static final String PROCESSING_PREFIX = "processing:";
    
    private RedisCache() {
        cache = new ConcurrentHashMap<>();
    }
    
    public static RedisCache getInstance() {
        if (instance == null) {
            synchronized (RedisCache.class) {
                if (instance == null) {
                    instance = new RedisCache();
                }
            }
        }
        return instance;
    }
    
    public void set(String key, String value) {
        cache.put(key, value);
    }
    
    public String get(String key) {
        return cache.get(key);
    }
    
    
    public boolean markAsProcessing(String cacheKey, String jobId) {
        String processingKey = PROCESSING_PREFIX + cacheKey;
        return cache.putIfAbsent(processingKey, jobId) == null;
    }
    
    public String getProcessingJobId(String cacheKey) {
        String processingKey = PROCESSING_PREFIX + cacheKey;
        return cache.get(processingKey);
    }
    
    public void clearProcessingMarker(String cacheKey) {
        String processingKey = PROCESSING_PREFIX + cacheKey;
        cache.remove(processingKey);
    }
}

