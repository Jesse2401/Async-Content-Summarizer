import strategy.HuggingFaceStrategy;
import strategy.SummaryStrategy;
import config.DatabaseConfiguration;

public class contentSummarizer {
    public static void main(String[] args) {
        System.out.println("Initializing database...");
        DatabaseConfiguration.initialize();
        
        // Test Hugging Face summarization
        try {
            SummaryStrategy strategy = new HuggingFaceStrategy();
            String testContent = "give definition of java in 50 words";
            
            System.out.println("\nGenerating summary for: " + testContent);
            String summary = strategy.generateSummary(testContent);
            System.out.println("\nSummary: " + summary);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\nApplication started successfully!");
    }
}