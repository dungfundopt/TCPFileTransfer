package com.example.shared;

import java.io.Serializable;

public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L; // Recommended for Serializable

    private final boolean success;
    private final String message;

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}