package com.example.shared;

import java.io.Serializable;
import java.time.LocalDateTime; // Sử dụng LocalDateTime cho thời gian
import java.time.format.DateTimeFormatter; // Để format hiển thị

public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filename;
    private long fileSize; // Kích thước file theo byte
    private String uploader; // Tên người upload
    private LocalDateTime uploadTime; // Thời gian upload

    // Constructor khi tạo FileMetadata từ Client hoặc Server
    public FileMetadata(String filename, long fileSize, String uploader, LocalDateTime uploadTime) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.uploader = uploader;
        this.uploadTime = uploadTime;
    }

    // Constructor khi nhận yêu cầu upload từ Client (chưa có uploader/uploadTime)
    public FileMetadata(String filename, long fileSize) {
         this(filename, fileSize, null, null);
    }


    // Getters
    public String getFilename() { return filename; }
    public long getFileSize() { return fileSize; }
    public String getUploader() { return uploader; }
    public LocalDateTime getUploadTime() { return uploadTime; }

    // Phương thức tiện ích để lấy kích thước định dạng đẹp hơn
    public String getFormattedFileSize() {
        if (fileSize <= 0) return "0 Bytes";
        final String[] units = new String[] { "Bytes", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));
        return String.format("%.2f %s", fileSize / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // Phương thức tiện ích để lấy thời gian định dạng đẹp hơn
    public String getFormattedUploadTime() {
         if (uploadTime == null) return "N/A";
         // Định dạng ngày giờ tùy theo ý bạn
         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
         return uploadTime.format(formatter);
    }


    // Có thể thêm setters nếu cần, nhưng thường immutable object tốt hơn

    @Override
    public String toString() {
        return "FileMetadata{" +
               "filename='" + filename + '\'' +
               ", fileSize=" + fileSize +
               ", uploader='" + uploader + '\'' +
               ", uploadTime=" + uploadTime +
               '}';
    }
}