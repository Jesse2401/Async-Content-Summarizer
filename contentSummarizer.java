import strategy.HuggingFaceStrategy;
import strategy.SummaryStrategy;
import config.DatabaseConfiguration;
import worker.JobWorker;

public class contentSummarizer {
    public static void main(String[] args) {
        System.out.println("Initializing database...");
        DatabaseConfiguration.initialize();
        
        // Start API Server
        int apiPort = 8080;
        if (args.length > 0) {
            try {
                apiPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: 8080");
            }
        }
        
        try {
            // Create service instance
            ContentSummarizerService service = new ContentSummarizerServiceImpl();
            
            // Start API Server
            ApiServer apiServer = new ApiServer(apiPort, service);
            apiServer.start();
            
            // Start Job Worker
            JobWorker jobWorker = new JobWorker();
            jobWorker.start();
            
            System.out.println("\nApplication started successfully!");
            System.out.println("API Server running on http://localhost:" + apiPort);
            System.out.println("Press Ctrl+C to stop...");
            
            // Keep the application running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                apiServer.stop();
                jobWorker.stop();
            }));
            
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}