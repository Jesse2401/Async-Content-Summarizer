import enums.JobStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Job {
    private String id;
    private String userId;
    private String inputContent;
    private boolean isUrl; 
    private String outputContent;
    private JobStatus status;

    public Job(String id, String userId, String inputContent, boolean isUrl, String outputContent, JobStatus status) {
        this.id = id;
        this.userId = userId;
        this.inputContent = inputContent;
        this.isUrl = isUrl;
        this.outputContent = outputContent;
        this.status = status;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", inputContent='" + inputContent + '\'' +
                ", isUrl=" + isUrl +
                ", outputContent='" + outputContent + '\'' +
                ", status=" + status +
                '}';
    }
}

