package com.example.shared;

import java.io.Serializable;

public class ServerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message; // Thông báo kết quả
    private final Serializable data;    // Chứa dữ liệu cụ thể cho từng loại phản hồi (FileList, FileData chunk, success status...)

    public ServerResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = (Serializable) data;
    }

    public ServerResponse(Object data, String message, boolean success) {
        this.data = (Serializable) data;
        this.message = message;
        this.success = success;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }

    // Add getters if needed
}