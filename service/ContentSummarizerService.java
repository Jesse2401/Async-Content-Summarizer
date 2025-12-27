package service;

import models.Job;
import models.User;
import enums.JobStatus;
import enums.UserType;
import strategy.SummaryStrategy;
import dao.JobDao;
import dao.UserDao;
import worker.QueueService;
import worker.RedisCache;
import util.CacheKeyGenerator;
import util.JsonUtils;
import util.TimeUtils;
import java.util.UUID;
import java.util.ArrayList;
import java.sql.Timestamp;

public abstract class ContentSummarizerService {
    
    protected JobDao jobDao = new JobDao();
    protected UserDao userDao = new UserDao();
    protected QueueService queueService = QueueService.getInstance();
    protected RedisCache redisCache = RedisCache.getInstance();
    
    protected abstract SummaryStrategy getStrategy();
    
    public void createUser(String userId, String name, UserType userType) throws Exception {
        // Check if user already exists
        User existingUser = userDao.findById(userId);
        if (existingUser != null) {
            throw new Exception("User already exists with id: " + userId);
        }
        
        // Create new user
        User newUser = new User(userId, name, new ArrayList<>(), userType);
        userDao.create(newUser);
    }
    
    public String submit(String userId, String text, boolean isUrl) throws Exception {
        String cacheKey = CacheKeyGenerator.generate(text, isUrl);
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
        String createdAtStr = TimeUtils.formatAsIso8601(createdAt);
        
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
        
        String cacheKey = CacheKeyGenerator.generate(job.getInputContent(), job.isUrl());
        boolean cached = redisCache.get(cacheKey) != null;
        long processingTimeMs = TimeUtils.calculateProcessingTimeMs(createdAt, updatedAt);
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"job_id\": \"").append(jobId).append("\",\n");
        json.append("  \"original_input\": \"").append(JsonUtils.escape(job.getInputContent())).append("\",\n");
        json.append("  \"summary\": \"").append(JsonUtils.escape(job.getOutputContent() != null ? job.getOutputContent() : "")).append("\",\n");
        json.append("  \"cached\": ").append(cached).append(",\n");
        json.append("  \"processing_time_ms\": ").append(processingTimeMs).append("\n");
        json.append("}");
        
        return json.toString();
    }
}

