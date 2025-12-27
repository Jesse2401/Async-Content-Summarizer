import config.DatabaseConfiguration;
import service.ContentSummarizerService;
import service.ContentSummarizerServiceImpl;
import api.ApiServer;
import worker.JobWorker;

public class Application {
    private static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) {
        System.out.println("Initializing database...");
        DatabaseConfiguration.initialize();
        
        int apiPort = parsePort(args);
        
        try {
            ContentSummarizerService service = new ContentSummarizerServiceImpl();
            ApiServer apiServer = new ApiServer(apiPort, service);
            apiServer.start();
            
            JobWorker jobWorker = new JobWorker();
            jobWorker.start();
            
            System.out.println("\nApplication started successfully!");
            System.out.println("API Server running on http://localhost:" + apiPort);
            System.out.println("Press Ctrl+C to stop...");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                apiServer.stop();
                jobWorker.stop();
            }));
            
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int parsePort(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }
}

