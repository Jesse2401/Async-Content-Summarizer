package dao;

import models.Job;
import enums.JobStatus;
import config.DatabaseConfiguration;
import java.sql.*;

public class JobDao {
    
    public void create(Job job) throws SQLException {
        String sql = "INSERT INTO jobs (id, userId, inputContent, isUrl, outputContent, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, job.getId());
            stmt.setString(2, job.getUserId());
            stmt.setString(3, job.getInputContent());
            stmt.setBoolean(4, job.isUrl());
            stmt.setString(5, job.getOutputContent());
            stmt.setString(6, job.getStatus().name());
            stmt.executeUpdate();
        }
    }
    
    public void updateStatus(String jobId, JobStatus status) throws SQLException {
        String sql = "UPDATE jobs SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, jobId);
            stmt.executeUpdate();
        }
    }
    
    public void updateOutput(String jobId, String outputContent) throws SQLException {
        String sql = "UPDATE jobs SET outputContent = ? WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, outputContent);
            stmt.setString(2, jobId);
            stmt.executeUpdate();
        }
    }
    
    public Job findById(String jobId) throws SQLException {
        String sql = "SELECT * FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJob(rs);
                }
                return null;
            }
        }
    }
    
    public Job findNextQueuedJob() throws SQLException {
        String sql = "SELECT * FROM jobs WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1";
        try (Connection conn = DatabaseConfiguration.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapResultSetToJob(rs);
            }
            return null;
        }
    }
    
    private Job mapResultSetToJob(ResultSet rs) throws SQLException {
        return new Job(
            rs.getString("id"),
            rs.getString("userId"),
            rs.getString("inputContent"),
            rs.getBoolean("isUrl"),
            rs.getString("outputContent"),
            JobStatus.valueOf(rs.getString("status"))
        );
    }
    
    public java.sql.Timestamp getCreatedAt(String jobId) throws SQLException {
        String sql = "SELECT createdAt FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("createdAt");
                }
                return null;
            }
        }
    }
    
    public java.sql.Timestamp getUpdatedAt(String jobId) throws SQLException {
        String sql = "SELECT updatedAt FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConfiguration.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("updatedAt");
                }
                return null;
            }
        }
    }
}

