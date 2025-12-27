package worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueService {
    private static QueueService instance;
    private BlockingQueue<String> jobQueue;
    
    private QueueService() {
        jobQueue = new LinkedBlockingQueue<>();
    }
    
    public static QueueService getInstance() {
        if (instance == null) {
            synchronized (QueueService.class) {
                if (instance == null) {
                    instance = new QueueService();
                }
            }
        }
        return instance;
    }
    
    public void enqueue(String jobId) {
        jobQueue.offer(jobId);
    }
    
    public String dequeue() throws InterruptedException {
        return jobQueue.take();
    }
    
    public String poll() {
        return jobQueue.poll();
    }
}

