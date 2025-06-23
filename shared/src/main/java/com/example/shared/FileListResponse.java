package com.example.shared;

import java.io.Serializable;
import java.util.List;

public class FileListResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<FileMetadata> fileList; // Danh sách các FileMetadata

    public FileListResponse(List<FileMetadata> fileList) {
        this.fileList = fileList;
    }

    public List<FileMetadata> getFileList() { return fileList; }
}