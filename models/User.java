package models;

import java.util.ArrayList;
import java.util.List;
import enums.UserType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private String id;
    private String name;
    private List<String> jobIDs;
    private UserType userType;

    public User(String id, String name, List<String> jobIDs, UserType userType) {
        this.id = id;
        this.name = name;
        this.jobIDs = jobIDs;
        this.userType = userType;
    }

    public void addJobID(String jobID) {
        if (this.jobIDs == null) {
            this.jobIDs = new ArrayList<>();
        }
        this.jobIDs.add(jobID);
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", jobIDs=" + jobIDs +
                ", userType=" + userType +
                '}';
    }
}

