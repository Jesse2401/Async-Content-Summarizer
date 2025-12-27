package dao;

import models.User;
import enums.UserType;
import config.DatabaseConfiguration;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {
    
    public void create(User user) throws SQLException {
        String sql = "INSERT INTO users (id, name, jobIDs, userType) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getName());
            stmt.setString(3, String.join(",", user.getJobIDs() != null ? user.getJobIDs() : new ArrayList<>()));
            stmt.setString(4, user.getUserType().name());
            stmt.executeUpdate();
        }
    }
    
    public User findById(String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
            return null;
        }
    }
    
    public void addJobId(String userId, String jobId) throws SQLException {
        User user = findById(userId);
        if (user != null) {
            user.addJobID(jobId);
            String sql = "UPDATE users SET jobIDs = ? WHERE id = ?";
            try (Connection conn = DatabaseConfiguration.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, String.join(",", user.getJobIDs()));
                stmt.setString(2, userId);
                stmt.executeUpdate();
            }
        }
    }
    
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String jobIDsStr = rs.getString("jobIDs");
        List<String> jobIDs = new ArrayList<>();
        if (jobIDsStr != null && !jobIDsStr.isEmpty()) {
            String[] ids = jobIDsStr.split(",");
            for (String id : ids) {
                if (!id.trim().isEmpty()) {
                    jobIDs.add(id.trim());
                }
            }
        }
        return new User(
            rs.getString("id"),
            rs.getString("name"),
            jobIDs,
            UserType.valueOf(rs.getString("userType"))
        );
    }
}

