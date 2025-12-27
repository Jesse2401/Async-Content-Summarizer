import models.Job;
import enums.JobStatus;
import strategy.SummaryStrategy;
import strategy.HuggingFaceStrategy;
import dao.JobDao;
import dao.UserDao;
import worker.QueueService;
import worker.RedisCache;
import java.util.UUID;
import java.security.MessageDigest;

public abstract class ContentSummarizerService {
    
    protected JobDao jobDao = new JobDao();
    protected UserDao userDao = new UserDao();
    protected QueueService queueService = QueueService.getInstance();
    protected RedisCache redisCache = RedisCache.getInstance();
    
    protected abstract SummaryStrategy getStrategy();
    
    public String submit(String userId, String text, boolean isUrl) throws Exception {
        // Check cache first
        String cacheKey = generateCacheKey(text, isUrl);
        String cachedSummary = redisCache.get(cacheKey);
        
        if (cachedSummary != null) {
            // Cache hit - create job with cached result and mark as completed
            String jobId = UUID.randomUUID().toString();
            Job job = new Job(jobId, userId, text, isUrl, cachedSummary, JobStatus.COMPLETED);
            
            jobDao.create(job);
            userDao.addJobId(userId, jobId);
            
            return jobId;
        }
        
        // Cache miss - proceed with normal flow
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, userId, text, isUrl, null, JobStatus.QUEUED);
        
        jobDao.create(job);
        userDao.addJobId(userId, jobId);
        queueService.enqueue(jobId);
        
        return jobId;
    }
    
    private String generateCacheKey(String text, boolean isUrl) {
        try {
            String input = (isUrl ? "url:" : "text:") + text;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return "content:" + sb.toString();
        } catch (Exception e) {
            // Fallback to simple key
            return "content:" + (isUrl ? "url:" : "text:") + text.hashCode();
        }
    }
}

