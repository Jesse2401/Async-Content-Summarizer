package worker;

import models.Job;
import enums.JobStatus;
import dao.JobDao;
import strategy.SummaryStrategy;
import strategy.HuggingFaceStrategy;
import util.CacheKeyGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JobWorker {
    private JobDao jobDao;
    private QueueService queueService;
    private RedisCache redisCache;
    private SummaryStrategy strategy;
    private HttpClient httpClient;
    private boolean running;
    
    public JobWorker() {
        this.jobDao = new JobDao();
        this.queueService = QueueService.getInstance();
        this.redisCache = RedisCache.getInstance();
        this.strategy = new HuggingFaceStrategy();
        this.httpClient = HttpClient.newHttpClient();
        this.running = true;
    }
    
    public void start() {
        new Thread(this::processJobs).start();
    }
    
    public void stop() {
        running = false;
    }
    
    private void processJobs() {
        while (running) {
            try {
                String jobId = queueService.poll();
                if (jobId == null) {
                    jobId = getNextQueuedJobId();
                }
                if (jobId != null) {
                    processJob(jobId);
                } else {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error processing job: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private String getNextQueuedJobId() {
        try {
            Job job = jobDao.findNextQueuedJob();
            return job != null ? job.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void processJob(String jobId) throws Exception {
        Job job = jobDao.findById(jobId);
        if (job == null) return;
        
        String cacheKey = CacheKeyGenerator.generate(job.getInputContent(), job.isUrl());
        String cachedSummary = redisCache.get(cacheKey);
        
        if (cachedSummary != null) {
            // Cache hit - use cached result
            jobDao.updateOutput(jobId, cachedSummary);
            jobDao.updateStatus(jobId, JobStatus.COMPLETED);
            return;
        }
        
        jobDao.updateStatus(jobId, JobStatus.PROCESSING);
        
        try {
            String content = fetchContent(job);
            String summary = strategy.generateSummary(content);
            
            jobDao.updateOutput(jobId, summary);
            jobDao.updateStatus(jobId, JobStatus.COMPLETED);
            
            redisCache.set(cacheKey, summary);
            redisCache.set("job:" + jobId, summary);
        } catch (Exception e) {
            jobDao.updateStatus(jobId, JobStatus.FAILED);
            throw e;
        }
    }
    
    private String fetchContent(Job job) throws Exception {
        if (!job.isUrl()) {
            // Direct text content - return as is
            return job.getInputContent();
        }
        
        // URL content - fetch from URL
        String url = job.getInputContent();
        if (url == null || url.trim().isEmpty()) {
            throw new Exception("URL is empty");
        }
        
        // Basic URL validation - must start with http:// or https://
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw new Exception("Invalid URL format. URL must start with http:// or https://");
        }
        
        return fetchFromUrl(trimmedUrl);
    }
    
    private String fetchFromUrl(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        throw new Exception("Failed to fetch content from URL: " + url);
    }
}

