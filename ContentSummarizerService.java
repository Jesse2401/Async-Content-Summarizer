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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

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
    
    public String getStatus(String jobId) throws Exception {
        Job job = jobDao.findById(jobId);
        if (job == null) {
            throw new Exception("Job not found");
        }
        
        Timestamp createdAt = jobDao.getCreatedAt(jobId);
        String createdAtStr = formatTimestamp(createdAt);
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"job_id\": \"").append(jobId).append("\",\n");
        json.append("  \"status\": \"").append(job.getStatus().name().toLowerCase()).append("\",\n");
        json.append("  \"created_at\": \"").append(createdAtStr).append("\"\n");
        json.append("}");
        
        return json.toString();
    }
    
    public String getResult(String jobId) throws Exception {
        Job job = jobDao.findById(jobId);
        if (job == null) {
            throw new Exception("Job not found");
        }
        
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new Exception("Job is not completed yet. Current status: " + job.getStatus().name().toLowerCase());
        }
        
        Timestamp createdAt = jobDao.getCreatedAt(jobId);
        Timestamp updatedAt = jobDao.getUpdatedAt(jobId);
        
        // Check if result was cached
        String cacheKey = generateCacheKey(job.getInputContent(), job.isUrl());
        boolean cached = redisCache.get(cacheKey) != null;
        
        // Calculate processing time in milliseconds
        long processingTimeMs = 0;
        if (createdAt != null && updatedAt != null) {
            processingTimeMs = updatedAt.getTime() - createdAt.getTime();
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"job_id\": \"").append(jobId).append("\",\n");
        json.append("  \"original_input\": \"").append(escapeJson(job.getInputContent())).append("\",\n");
        json.append("  \"summary\": \"").append(escapeJson(job.getOutputContent() != null ? job.getOutputContent() : "")).append("\",\n");
        json.append("  \"cached\": ").append(cached).append(",\n");
        json.append("  \"processing_time_ms\": ").append(processingTimeMs).append("\n");
        json.append("}");
        
        return json.toString();
    }
    
    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
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

