package com.example.server; // Đảm bảo đúng package

import java.sql.Connection; // Import FileMetadata
import java.sql.DriverManager;
import java.sql.PreparedStatement; // Cần import
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.shared.FileMetadata;

public class DatabaseHandler {

    private static final String DB_URL = "jdbc:sqlite:server_database.db"; // Đổi tên file DB cho server

    // Bảng Users (giữ nguyên)
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD = "password";

    // Bảng Files (thêm mới)
    private static final String TABLE_FILES = "files";
    private static final String FILE_COLUMN_ID = "id"; // Khóa chính auto-increment
    private static final String FILE_COLUMN_FILENAME = "filename";
    private static final String FILE_COLUMN_SIZE = "file_size";
    private static final String FILE_COLUMN_UPLOADER = "uploader";
    private static final String FILE_COLUMN_UPLOAD_TIME = "upload_time";

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public DatabaseHandler() {
        createNewTables(); // Gọi phương thức tạo cả hai bảng
    }

    @SuppressWarnings("CallToPrintStackTrace")
     private Connection connect() {
        // ... (Giữ nguyên phương thức connect) ...
         Connection conn = null;
         try {
             conn = DriverManager.getConnection(DB_URL);
             
         } catch (SQLException e) {
            e.printStackTrace();
             
         }
         return conn;
     }


    /**
     * Tạo các bảng (users và files) nếu chúng chưa tồn tại.
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public void createNewTables() {
        // SQL cho bảng users (giữ nguyên hoặc sửa lại nếu cần id...)
        String createUserTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
                + COLUMN_USERNAME + " TEXT PRIMARY KEY,"
                + COLUMN_PASSWORD + " TEXT NOT NULL"
                + ");";

        // SQL cho bảng files (thêm mới)
        String createFileTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_FILES + " ("
                + FILE_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," // Id tự tăng
                + FILE_COLUMN_FILENAME + " TEXT NOT NULL UNIQUE," // Tên file là duy nhất
                + FILE_COLUMN_SIZE + " INTEGER NOT NULL,"
                + FILE_COLUMN_UPLOADER + " TEXT NOT NULL,"
                + FILE_COLUMN_UPLOAD_TIME + " TIMESTAMP NOT NULL," // Lưu thời gian
                + "FOREIGN KEY(" + FILE_COLUMN_UPLOADER + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USERNAME + ")"
                + ");";

        try (Connection conn = connect()) {
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createUserTableSql);
                    stmt.execute(createFileTableSql); // Tạo bảng files
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ... (Giữ nguyên phương thức verifyUser) ...
    // Phương thức verifyUser(String username, String password)

     /**
      * Xác minh thông tin đăng nhập của người dùng (cho chức năng Đăng nhập).
      * LƯU Ý QUAN TRỌNG: BẠN PHẢI SO SÁNH MẬT KHẨU BĂM (HASH) SAU KHI BĂM MẬT KHẨU ĐẦU VÀO!
      * @param username Tên đăng nhập
      * @param password Mật khẩu (chưa băm, sẽ băm hoặc dùng cách khác để so sánh với mật khẩu băm trong DB)
      * @return true nếu thông tin hợp lệ, false nếu không
      */
     public boolean verifyUser(String username, String password) {
        // ...existing code...
    String sql = String.format(
        "SELECT %s FROM %s WHERE %s = ?",
        COLUMN_PASSWORD, TABLE_USERS, COLUMN_USERNAME
    );
// ...existing code...

        try (Connection conn = connect();
             PreparedStatement pstmt = conn != null ? conn.prepareStatement(sql) : null) {
            if (pstmt == null) {
            return false;
            }
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
            String storedPasswordHash = rs.getString(COLUMN_PASSWORD);
            // !!! Chỗ này cần code để BĂM mật khẩu 'password' và SO SÁNH với 'storedPasswordHash' !!!
            // Vì lý do đơn giản, ta tạm so sánh chuỗi (KHÔNG AN TOÀN)
            return password.equals(storedPasswordHash); // <-- KHÔNG AN TOÀN

            
            // Nếu muốn so sánh đúng, chỉ cần return password.equals(storedPasswordHash); (đã có ở trên)
            // Nếu bạn muốn dùng biến passwordMatches, hãy khai báo và gán giá trị cho nó:
            
            
            // Nhưng ở đây chỉ cần:
            // (Không cần thêm gì ở đây, vì đã return ở trên)
            } else {
            return false; // Không tìm thấy username
            }

        } catch (SQLException e) {
            return false;
        }
     }

    // ... (Giữ nguyên phương thức addUser, có thể sửa để kiểm tra username tồn tại trước) ...
     /**
      * Thêm người dùng mới vào database (cho chức năng Đăng ký).
      * LƯU Ý QUAN TRỌNG: TRONG ỨNG DỤNG THỰC TẾ, BẠN PHẢI BĂM (HASH) MẬT KHẨU TRƯỚC KHI LƯU VÀO DB!
      * @param username Tên đăng nhập
      * @param password Mật khẩu (cần băm trong thực tế)
      * @return true nếu thêm thành công, false nếu có lỗi hoặc username đã tồn tại
      */
     public boolean addUser(String username, String password) {
         // Kiểm tra xem username đã tồn tại chưa
         if (usernameExists(username)) {
             
             return false;
         }

         String sql = "INSERT INTO " + TABLE_USERS + "(" + COLUMN_USERNAME + ", " + COLUMN_PASSWORD + ") VALUES(?, ?)";

         try (Connection conn = connect();
              PreparedStatement pstmt = conn != null ? conn.prepareStatement(sql) : null) {
             if (pstmt == null) {
                 return false;
             }
             pstmt.setString(1, username);
             // !!! Băm (HASH) mật khẩu ở đây trước khi setString(2) !!!
             pstmt.setString(2, password); // <-- Lưu mật khẩu chưa băm (KHÔNG AN TOÀN)

             int rowsAffected = pstmt.executeUpdate();
             return rowsAffected > 0;

         } catch (SQLException e) {
             return false;
         }
     }
      /**
      * Kiểm tra xem username đã tồn tại trong database chưa.
      * @param username Tên đăng nhập cần kiểm tra
      * @return true nếu tồn tại, false nếu không
      */
    @SuppressWarnings("CallToPrintStackTrace")
     private boolean usernameExists(String username) {
         String sql = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE " + COLUMN_USERNAME + " = ?";
         try (Connection conn = connect();
              PreparedStatement pstmt = conn != null ? conn.prepareStatement(sql) : null) {
             if (pstmt == null) {
                 return false;
             }
             pstmt.setString(1, username);
             ResultSet rs = pstmt.executeQuery();
             if (rs.next()) {
                 return rs.getInt(1) > 0;
             }
         } catch (SQLException e) {
             e.printStackTrace();
         }
         return false;
     }


    /**
     * Lưu thông tin file vào database sau khi upload thành công.
     * @param metadata Thông tin file (Filename, Size, Uploader, UploadTime).
     * @return true nếu lưu thành công, false nếu có lỗi.
     */
    public boolean addFileMetadata(FileMetadata metadata) {
        String sql = "INSERT INTO " + TABLE_FILES + "(" + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME + ") VALUES(?, ?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, metadata.getFilename());
            pstmt.setLong(2, metadata.getFileSize());
            pstmt.setString(3, metadata.getUploader());
            // Chuyển LocalDateTime sang java.sql.Timestamp
            pstmt.setTimestamp(4, Timestamp.valueOf(metadata.getUploadTime()));

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
             
             // Lỗi có thể do UNIQUE constraint trên filename nếu đã tồn tại file cùng tên
             return false;
        }
    }

    /**
     * Lấy toàn bộ thông tin các file đã upload từ database.
     * @return Danh sách FileMetadata, trả về danh sách rỗng nếu không có file hoặc có lỗi.
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public List<FileMetadata> getAllFileMetadata() {
        String sql = "SELECT " + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME + " FROM " + TABLE_FILES + " ORDER BY " + FILE_COLUMN_UPLOAD_TIME + " DESC"; // Sắp xếp theo thời gian mới nhất

        List<FileMetadata> fileList = new ArrayList<>();
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) { // Dùng executeQuery cho SQL_SELECT

            while (rs.next()) {
                String filename = rs.getString(FILE_COLUMN_FILENAME);
                long fileSize = rs.getLong(FILE_COLUMN_SIZE);
                String uploader = rs.getString(FILE_COLUMN_UPLOADER);
                // Chuyển java.sql.Timestamp sang LocalDateTime
                LocalDateTime uploadTime = rs.getTimestamp(FILE_COLUMN_UPLOAD_TIME).toLocalDateTime();

                fileList.add(new FileMetadata(filename, fileSize, uploader, uploadTime));
            }

        } catch (SQLException e) {
            e.printStackTrace();
             
        }
        return fileList;
    }

     /**
     * Tìm kiếm thông tin file theo tên trong database.
     * @param query Chuỗi tìm kiếm (sẽ dùng LIKE %query%).
     * @return Danh sách FileMetadata khớp với tìm kiếm.
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public List<FileMetadata> searchFileMetadata(String query) {
        // Sử dụng LIKE để tìm kiếm gần đúng, UPPER để không phân biệt hoa/thường
        String sql = "SQL_SELECT " + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME +
                     " FROM " + TABLE_FILES +
                     " WHERE UPPER(" + FILE_COLUMN_FILENAME + ") LIKE ? OR UPPER(" + FILE_COLUMN_UPLOADER + ") LIKE ?" + // Tìm kiếm theo tên file hoặc tên uploader
                     " ORDER BY " + FILE_COLUMN_UPLOAD_TIME + " DESC";

        List<FileMetadata> fileList = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Đặt giá trị cho dấu hỏi, thêm % cho LIKE
            pstmt.setString(1, "%" + query.toUpperCase() + "%");
             pstmt.setString(2, "%" + query.toUpperCase() + "%");

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String filename = rs.getString(FILE_COLUMN_FILENAME);
                long fileSize = rs.getLong(FILE_COLUMN_SIZE);
                String uploader = rs.getString(FILE_COLUMN_UPLOADER);
                LocalDateTime uploadTime = rs.getTimestamp(FILE_COLUMN_UPLOAD_TIME).toLocalDateTime();

                fileList.add(new FileMetadata(filename, fileSize, uploader, uploadTime));
            }

        } catch (SQLException e) {
            e.printStackTrace();
             
        }
        return fileList;
    }

     /**
      * Lấy thông tin file theo tên file chính xác.
      * @param filename Tên file cần tìm.
      * @return FileMetadata hoặc null nếu không tìm thấy.
      */
    @SuppressWarnings("CallToPrintStackTrace")
     public FileMetadata getFileMetadataByName(String filename) {
          String sql = "SELECT " + FILE_COLUMN_FILENAME + ", " + FILE_COLUMN_SIZE + ", " + FILE_COLUMN_UPLOADER + ", " + FILE_COLUMN_UPLOAD_TIME +
                      " FROM " + TABLE_FILES +
                      " WHERE " + FILE_COLUMN_FILENAME + " = ?";

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
            e.printStackTrace();
              
          }
          return null; // Không tìm thấy
     }

     
}