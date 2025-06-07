package com.example.client;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption; // Cần cho callback
import java.util.function.BiConsumer;

import com.example.shared.AuthRequest;
import com.example.shared.FileMetadata;
import com.example.shared.FileTransferPacket;
import com.example.shared.RequestType;
import com.example.shared.ServerRequest;
import com.example.shared.ServerResponse;

public class ServerClient implements Closeable {

    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private boolean isConnected = false;
    private String loggedInUsername = null;

    public ServerClient() {
        // Kết nối được thực hiện trong authenticate() hoặc register()
    }

    public boolean connect() {
        try {
            if (!isConnected || socket == null || socket.isClosed()) {
                 socket = new Socket(SERVER_IP, SERVER_PORT);
                 // Thứ tự tạo ObjectOutputStream và ObjectInputStream rất quan trọng
                 // OOS phải được tạo trước OIS trên cả hai đầu kết nối (client và server)
                 oos = new ObjectOutputStream(socket.getOutputStream());
                 oos.flush(); // Đảm bảo header OOS được gửi đi

                 ois = new ObjectInputStream(socket.getInputStream());
                 isConnected = true;
                 
            }
            return true;
        } catch (IOException e) {
            
            closeConnection();
            return false;
        }
    }

    public void closeConnection() {
        loggedInUsername = null;
        isConnected = false;
        try {
            if (oos != null) { 
                oos.close(); 
                java.util.logging.Logger.getLogger(ServerClient.class.getName()).info("OOS closed.");
            }
            if (ois != null) { 
                ois.close(); 
                java.util.logging.Logger.getLogger(ServerClient.class.getName()).info("OIS closed.");
            }
            if (socket != null && !socket.isClosed()) { 
                socket.close(); 
                java.util.logging.Logger.getLogger(ServerClient.class.getName()).info("Socket closed.");
            }
        } catch (IOException e) {
            // Exception ignored because we are closing resources and cannot do much here.
        } finally {
             oos = null;
             ois = null;
             socket = null;
        }
    }

    @Override
    public void close() throws IOException {
        closeConnection();
    }

    /**
     * Gửi yêu cầu chung và nhận phản hồi. KHÔNG DÙNG cho file transfer byte stream.
     * @param request Yêu cầu ServerRequest.
     * @return ServerResponse từ Server hoặc null nếu có lỗi giao tiếp.
     */
    private ServerResponse sendAndReceive(ServerRequest request) {
        if (!isConnected) {
             java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
             logger.severe("Cannot send request: Not connected to server.");
             return new ServerResponse(false, "Not connected to server.", null);
        }
        try {
            this.oos.writeObject(request);
            this.oos.flush();

            Object received = this.ois.readObject();
             if (!(received instanceof ServerResponse)) {
                 java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
                 logger.log(java.util.logging.Level.SEVERE, "Received unexpected object type: {0}", (received != null ? received.getClass().getName() : "null"));
                  throw new IOException("Received unexpected data from server.");
             }

            return (ServerResponse) received;

        } catch (IOException | ClassNotFoundException e) {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
            logger.log(java.util.logging.Level.SEVERE, "Communication error during send/receive: {0}", e.getMessage());
            closeConnection();
            return null;
        }
    }

    /**
     * Xác thực đăng nhập.
     * @param username Tên đăng nhập.
     * @param password Mật khẩu.
     * @return ServerResponse từ Server.
     */
    @SuppressWarnings("exports")
    public ServerResponse authenticate(String username, String password) {
        if (!connect()) {
            return new ServerResponse(false, "Failed to connect to server.", null);
        }
        // AuthRequest chỉ chứa username và password
        ServerRequest request = new ServerRequest(RequestType.LOGIN, new AuthRequest(username, password));
        ServerResponse response = sendAndReceive(request);

        if (response != null && response.isSuccess() && response.getData() instanceof String) {
            this.loggedInUsername = (String) response.getData();
        } else {
            this.loggedInUsername = null;
             closeConnection(); // Giả định server đóng kết nối nếu login thất bại.
        }
        return response;
    }

    /**
     * Gửi yêu cầu đăng ký.
     * @param username Tên đăng nhập.
     * @param password Mật khẩu.
     * @return ServerResponse từ Server.
     */
    @SuppressWarnings("exports")
    public ServerResponse register(String username, String password) {
         if (!connect()) {
             return new ServerResponse(false, "Failed to connect to server.", null);
         }
         // AuthRequest chỉ chứa username và password
        ServerRequest request = new ServerRequest(RequestType.REGISTER, new AuthRequest(username, password));
        ServerResponse response = sendAndReceive(request);
        closeConnection(); // Giả định server đóng kết nối sau khi đăng ký xong
        return response;
    }

     /**
      * Gửi yêu cầu đăng xuất.
      * @return ServerResponse từ Server.
      */
     @SuppressWarnings("exports")
    public ServerResponse logout() {
         if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(true, "Already logged out or not connected.", null);
         }
         // Có thể gửi username để server xác định session nào
         ServerRequest request = new ServerRequest(RequestType.LOGOUT, loggedInUsername);
         ServerResponse response = sendAndReceive(request); // ServerHandler sẽ đóng kết nối sau khi xử lý logout
         closeConnection(); // Đóng kết nối phía client
         return response;
     }


    /**
     * Lấy danh sách file từ Server.
     * @return ServerResponse chứa FileListResponse hoặc lỗi.
     */
    @SuppressWarnings("exports")
    public ServerResponse getFileList() {
        if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(false, "Not connected or not logged in.", null);
        }
        ServerRequest request = new ServerRequest(RequestType.LIST_FILES, null);
        return sendAndReceive(request);
    }

     /**
     * Tìm kiếm file trên Server.
     * @param query Chuỗi tìm kiếm.
     * @return ServerResponse chứa FileListResponse hoặc lỗi.
     */
    @SuppressWarnings("exports")
    public ServerResponse searchFiles(String query) {
        if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(false, "Not connected or not logged in.", null);
        }
        ServerRequest request = new ServerRequest(RequestType.SEARCH_FILES, query);
        return sendAndReceive(request);
    }

     /**
      * Gửi file lên Server, báo cáo tiến độ. Phải chạy trong luồng nền.
      * ServerClient sẽ quản lý việc gửi byte và gọi callback tiến độ.
      * @param fileToSend File cục bộ cần gửi.
      * @param progressCallback Callback để báo cáo tiến độ (byte sent, total size). Có thể null.
      * @return ServerResponse cuối cùng từ Server sau khi upload xong metadata.
      * @throws IOExó lỗi I/O.
      * @throws ClassNotFoundException Nếu nhận object lỗi.
      * @throws Exception Nếu server báo lỗi trong quá trình.
      */
     @SuppressWarnings("exports")
    public ServerResponse uploadFile(File fileToSend, BiConsumer<Long, Long> progressCallback)
             throws Exception {

          if (!isConnected || loggedInUsername == null) {
               throw new IllegalStateException("Not connected or not logged in.");
          }
           if (fileToSend == null || !fileToSend.exists()) {
               throw new IllegalArgumentException("Invalid file selected for upload.");
           }

          String filename = fileToSend.getName();
          long fileSize = fileToSend.length();

           java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
           if (logger.isLoggable(java.util.logging.Level.INFO)) {
               logger.log(java.util.logging.Level.INFO, "Client: Sending UPLOAD_FILE request for {0}", filename);
           }
           // 1. Gửi yêu cầu UPLOAD_FILE kèm metadata ban đầu
           this.oos.writeObject(new ServerRequest(RequestType.UPLOAD_FILE, new FileMetadata(filename, fileSize)));
           this.oos.flush();

           // 2. Nhận phản hồi "Ready to receive" từ Server
           Object initialReceived = this.ois.readObject();
            if (!(initialReceived instanceof ServerResponse)) {
                 throw new IOException("Received unexpected object from server after upload request.");
            }
           ServerResponse initialResponse = (ServerResponse) initialReceived;

           if (!initialResponse.isSuccess()) {
               throw new ServerUploadException("Server refused upload: " + initialResponse.getMessage());
           }
           if (logger.isLoggable(java.util.logging.Level.INFO)) {
               logger.info("Client: Server is ready to receive file.");
           }

           // 3. Gửi dữ liệu file theo gói
           byte[] buffer = new byte[4096];
           long totalSent = 0;

           try (InputStream fis = new FileInputStream(fileToSend)) {
               int bytesRead;
               while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] dataToSend = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                   boolean isLast = (totalSent + dataToSend.length) >= fileSize; // Check using totalSent + bytesRead + currentDataLength vs fileSize

                   FileTransferPacket packet = new FileTransferPacket(filename, dataToSend, 0, 0, fileSize, isLast);

                   this.oos.writeObject(packet);
                   this.oos.flush();

                   totalSent += dataToSend.length;

                   // !!! Gọi callback để báo cáo tiến độ !!!
                   if (progressCallback != null) {
                       progressCallback.accept(totalSent, fileSize);
                   }

                   // Thêm delay nhỏ tùy chọn
                   
               }
           } // fis sẽ tự đóng

           if (logger.isLoggable(java.util.logging.Level.INFO)) {
               logger.log(java.util.logging.Level.INFO, "Client: Finished sending file data for {0}, total sent: {1}", new Object[]{filename, totalSent});
           }

           // 4. Nhận phản hồi kết quả upload cuối cùng từ Server
           Object finalReceived = this.ois.readObject();
           if (!(finalReceived instanceof ServerResponse)) {
                throw new IOException("Received unexpected object from server after file data sent.");
           }
           return (ServerResponse) finalReceived;
       }

        /**
         * Tải file từ Server, báo cáo tiến độ. Phải chạy trong luồng nền.
         * ServerClient sẽ quản lý việc nhận byte và gọi callback tiến độ.
         * @param filenameToDownload Tên file trên Server cần tải.
         * @param saveFile Vị trí cục bộ để lưu file.
         * @param progressCallback Callback để báo cáo tiến độ (byte received, total size). Có thể null.
         * @return FileMetadata của file đã tải (được trả về từ Server).
         * @throws IOException Nếu có lỗi I/O.
         * @throws ClassNotFoundException Nếu nhận object lỗi.
         * @throws Exception Nếu server báo lỗi hoặc file size mismatch.
         */
    @SuppressWarnings({ "LoggerStringConcat", "exports" })
    public FileMetadata downloadFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback)
            throws Exception {

        validateDownloadParams(filenameToDownload, saveFile);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info("Client: Sending DOWNLOAD_FILE request for " + filenameToDownload);
        }

        this.oos.writeObject(new ServerRequest(RequestType.DOWNLOAD_FILE, filenameToDownload));
        this.oos.flush();

        ServerResponse initialResponse = receiveDownloadResponse(saveFile);
        FileMetadata downloadedFileMetadata = (FileMetadata) initialResponse.getData();
        long expectedSize = downloadedFileMetadata.getFileSize();

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.log(java.util.logging.Level.INFO, "Client: Server is ready to send file: {0} ({1} bytes)",
                    new Object[] { downloadedFileMetadata.getFilename(), expectedSize });
        }

        long totalReceived = receiveFileData(saveFile, expectedSize, progressCallback, logger);

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(String.format("Client: Finished receiving file data for %s, total received: %d",
                    filenameToDownload, totalReceived));
        }

        verifyFileSize(totalReceived, expectedSize, saveFile, filenameToDownload, logger);

        return downloadedFileMetadata;
    }

    private void validateDownloadParams(String filenameToDownload, File saveFile) {
        if (!isConnected || loggedInUsername == null) {
            throw new IllegalStateException("Not connected or not logged in.");
        }
        if (filenameToDownload == null || filenameToDownload.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty for download.");
        }
        if (saveFile == null) {
            throw new IllegalArgumentException("Save location cannot be null.");
        }
    }

    private ServerResponse receiveDownloadResponse(File saveFile) throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
        Object initialReceived = this.ois.readObject();
        if (!(initialReceived instanceof ServerResponse)) {
            logger.severe("Client: Received unexpected object from server after download request.");
            throw new IOException("Received unexpected data from server after download request.");
        }
        ServerResponse initialResponse = (ServerResponse) initialReceived;

        if (!initialResponse.isSuccess() || !(initialResponse.getData() instanceof FileMetadata)) {
            if (Files.exists(saveFile.toPath())) {
                try {
                    Files.delete(saveFile.toPath());
                } catch (IOException deleteEx) {
                    logger.log(java.util.logging.Level.WARNING, "Failed to delete empty partial file: {0}", deleteEx.getMessage());
                }
            }
            throw new ServerDownloadException(
                    "Server refused download or did not provide metadata: " + initialResponse.getMessage());
        }
        return initialResponse;
    }

    private long receiveFileData(File saveFile, long expectedSize, BiConsumer<Long, Long> progressCallback,
            java.util.logging.Logger logger) throws Exception {
        long totalReceived = 0;
        try (OutputStream fos = Files.newOutputStream(saveFile.toPath(), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            FileTransferPacket packet;
            while (true) {
                Object receivedObject = this.ois.readObject();
                if (!(receivedObject instanceof FileTransferPacket)) {
                    logger.severe("Client: Received unexpected object during download.");
                    throw new IOException("Received unexpected data from server during download.");
                }
                packet = (FileTransferPacket) receivedObject;

                if (packet.getData() != null && packet.getData().length > 0) {
                    fos.write(packet.getData());
                    totalReceived += packet.getData().length;

                    if (progressCallback != null) {
                        progressCallback.accept(totalReceived, expectedSize);
                    }
                }

                if (packet.isLastPacket()) {
                    break;
                }
            }
        }
        return totalReceived;
    }

    private void verifyFileSize(long totalReceived, long expectedSize, File saveFile, String filenameToDownload,
            java.util.logging.Logger logger) throws IOException {
        if (totalReceived != expectedSize) {
            if (logger.isLoggable(java.util.logging.Level.SEVERE)) {
                logger.severe(String.format("Client: File size mismatch for %s. Expected: %d, Received: %d",
                        filenameToDownload, expectedSize, totalReceived));
            }
            Files.deleteIfExists(saveFile.toPath());
            throw new IOException("File size mismatch during download.");
        }
    }


    public String getLoggedInUsername() {
        return loggedInUsername;
    }

    public boolean isConnected() {
        return isConnected;
    }
}

/**
 * Custom exception for upload errors to the server.
 */
class ServerUploadException extends Exception {
    public ServerUploadException(String message) {
        super(message);
    }
}

/**
 * Custom exception for download errors from the server.
 */
class ServerDownloadException extends Exception {
    public ServerDownloadException(String message) {
        super(message);
    }
}