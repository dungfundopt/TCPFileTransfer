package com.example.client; // Thay đổi package cho phù hợp với cấu trúc project của bạn

public class FileTransferException extends Exception {

    public FileTransferException(String message) {
        super(message);
    }

    public FileTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}