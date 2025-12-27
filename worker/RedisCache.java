package worker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class RedisCache {
    private static RedisCache instance;
    private Map<String, String> cache;
    
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
}

