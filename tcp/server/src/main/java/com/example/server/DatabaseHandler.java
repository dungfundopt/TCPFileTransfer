package com.example.server;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.example.shared.FileMetadata;

public class DatabaseHandler {

    private static final String DB_URL = "jdbc:sqlite:server_database.db";
    private static final Logger LOGGER = Logger.getLogger(DatabaseHandler.class.getName());
    private static final String WHERE = " WHERE ";

    private static final String TABLE_USERS = "users";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD = "password";

    private static final String TABLE_FILES = "files";
    private static final String FILE_COLUMN_ID = "id";
    private static final String FILE_COLUMN_FILENAME = "filename";
    private static final String FILE_COLUMN_SIZE = "file_size";
    private static final String FILE_COLUMN_UPLOADER = "uploader";
    private static final String FILE_COLUMN_UPLOAD_TIME = "upload_time";
    private static final String DTB_ERROR = "Lỗi cơ sở dữ liệu.";

    public DatabaseHandler() {
        createNewTables();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void createNewTables() {
        String createUserTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
                + COLUMN_USERNAME + " TEXT PRIMARY KEY,"
                + COLUMN_PASSWORD + " TEXT NOT NULL"
                + ");";

        String createFileTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_FILES + " ("
                + FILE_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + FILE_COLUMN_FILENAME + " TEXT NOT NULL UNIQUE,"
                + FILE_COLUMN_SIZE + " INTEGER NOT NULL,"
                + FILE_COLUMN_UPLOADER + " TEXT NOT NULL,"
                + FILE_COLUMN_UPLOAD_TIME + " TIMESTAMP NOT NULL,"
                + "FOREIGN KEY(" + FILE_COLUMN_UPLOADER + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USERNAME + ")"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUserTableSql);
            stmt.execute(createFileTableSql);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, DTB_ERROR, e);
        }
    }

    public boolean verifyUser(String username, String password) {
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                COLUMN_PASSWORD, TABLE_USERS, COLUMN_USERNAME
        );
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPasswordHash = rs.getString(COLUMN_PASSWORD);
                    return password.equals(storedPasswordHash);
                } else {
                    return false;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi cơ sở dữ liệu khi xác thực người dùng: ", e);
            return false;
        }
    }

    public boolean addUser(String username, String password) {
        if (usernameExists(username)) {
            return false;
        }
        String sql = "INSERT INTO " + TABLE_USERS + "(" + COLUMN_USERNAME + ", " + COLUMN_PASSWORD + ") VALUES(?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi cơ sở dữ liệu khi thêm người dùng: ", e);
            return false;
        }
    }

    private boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_USERS + WHERE + COLUMN_USERNAME + " = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi cơ sở dữ liệu khi kiểm tra người dùng tồn tại: ", e);
        }
        return false;
    }

    public boolean addFileMetadata(FileMetadata metadata) {
        String sql = "INSERT INTO " + TABLE_FILES + "(" + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME + ") VALUES(?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, metadata.getFilename());
            pstmt.setLong(2, metadata.getFileSize());
            pstmt.setString(3, metadata.getUploader());
            pstmt.setTimestamp(4, Timestamp.valueOf(metadata.getUploadTime()));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Không thể thêm metadata của file vào DB", e);
            return false;
        }
    }

    public List<FileMetadata> getAllFileMetadata(String username) {
        String sql = String.format(
                "SELECT %s, %s, %s, %s FROM %s WHERE %s = ? ORDER BY %s DESC",
                FILE_COLUMN_FILENAME, FILE_COLUMN_SIZE, FILE_COLUMN_UPLOADER, FILE_COLUMN_UPLOAD_TIME,
                TABLE_FILES, FILE_COLUMN_UPLOADER, FILE_COLUMN_UPLOAD_TIME
        );
        List<FileMetadata> fileList = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String filename = rs.getString(FILE_COLUMN_FILENAME);
                long fileSize = rs.getLong(FILE_COLUMN_SIZE);
                String uploader = rs.getString(FILE_COLUMN_UPLOADER);
                LocalDateTime uploadTime = rs.getTimestamp(FILE_COLUMN_UPLOAD_TIME).toLocalDateTime();
                fileList.add(new FileMetadata(filename, fileSize, uploader, uploadTime));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, DTB_ERROR, e);
        }
        return fileList;
    }

    public List<FileMetadata> searchFileMetadata(String query, String username) {
        String sql = "SELECT " + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME +
                " FROM " + TABLE_FILES +
                " WHERE (" +
                "UPPER(" + FILE_COLUMN_FILENAME + ") LIKE ? OR UPPER(" + FILE_COLUMN_UPLOADER + ") LIKE ?" +
                ") AND " + FILE_COLUMN_UPLOADER + " = ?" +
                " ORDER BY " + FILE_COLUMN_UPLOAD_TIME + " DESC";
        List<FileMetadata> fileList = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + query.toUpperCase() + "%");
            pstmt.setString(2, "%" + query.toUpperCase() + "%");
            pstmt.setString(3, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String filename = rs.getString(FILE_COLUMN_FILENAME);
                long fileSize = rs.getLong(FILE_COLUMN_SIZE);
                String uploader = rs.getString(FILE_COLUMN_UPLOADER);
                LocalDateTime uploadTime = rs.getTimestamp(FILE_COLUMN_UPLOAD_TIME).toLocalDateTime();
                fileList.add(new FileMetadata(filename, fileSize, uploader, uploadTime));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, DTB_ERROR, e);
        }
        return fileList;
    }

    public FileMetadata getFileMetadataByName(String filename) {
        String sql = "SELECT " + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME +
                " FROM " + TABLE_FILES +
                WHERE + FILE_COLUMN_FILENAME + " = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long fileSize = rs.getLong(FILE_COLUMN_SIZE);
                String uploader = rs.getString(FILE_COLUMN_UPLOADER);
                LocalDateTime uploadTime = rs.getTimestamp(FILE_COLUMN_UPLOAD_TIME).toLocalDateTime();
                return new FileMetadata(filename, fileSize, uploader, uploadTime);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, DTB_ERROR, e);
        }
        return null;
    }

    public boolean deleteFileMetadata(String filename, String username) {
        String sql = "DELETE FROM " + TABLE_FILES + WHERE + FILE_COLUMN_FILENAME + " = ? AND " + FILE_COLUMN_UPLOADER + " = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi xóa file: ", e);
            return false;
        }
    }
}