package com.example.shared;

import java.io.Serializable;

public class ServerRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final RequestType type;
    private final Serializable data; // Chứa dữ liệu cụ thể cho từng loại yêu cầu (username/pass, filename, FileMetadata, byte[])

    public ServerRequest(RequestType type, Object data) {
        this.type = type;
        this.data = (Serializable) data;
    }

    public RequestType getType() { return type; }
    public Object getData() { return data; }

    // Add getters/setters if needed, but immutable is fine for requests
}