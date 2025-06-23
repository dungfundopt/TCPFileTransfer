package com.example.shared;

import java.io.Serializable;

@SuppressWarnings("unused")
public enum RequestType implements Serializable {
    // Authentication
    LOGIN,
    LOGOUT,
    REGISTER,

    // File Management
    LIST_FILES,
    SEARCH_FILES,
    UPLOAD_FILE,
    DOWNLOAD_FILE,
    DELETE_FILE, // This is a placeholder for future use
    // Maybe others later like DELETE_FILE, RENAME_FILE...
}