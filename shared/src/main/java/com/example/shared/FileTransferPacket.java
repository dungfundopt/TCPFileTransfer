package com.example.shared;

import java.io.Serializable;

public class FileTransferPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String filename; // Tên file đang transfer
    private final byte[] data;     // Dữ liệu của gói
    private int sequenceNumber; // Số thứ tự gói
    private int totalPackets; // Tổng số gói dự kiến (cho progress bar)
    private long totalFileSize; // Tổng kích thước file (cho progress bar)
    private boolean isLastPacket; // Cờ báo gói cuối cùng

    public FileTransferPacket(String filename, byte[] data, int sequenceNumber, int totalPackets, long totalFileSize, boolean isLastPacket) {
        this.filename = filename;
        this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.totalPackets = totalPackets;
        this.totalFileSize = totalFileSize;
        this.isLastPacket = isLastPacket;
    }

    public FileTransferPacket(byte[] data, String filename) {
        this.data = data;
        this.filename = filename;
    }

    // Getters
    public String getFilename() { return filename; }
    public byte[] getData() { return data; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getTotalPackets() { return totalPackets; }
    public long getTotalFileSize() { return totalFileSize; }
    public boolean isLastPacket() { return isLastPacket; }
}