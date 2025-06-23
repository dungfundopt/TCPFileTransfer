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

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class ServerClient implements Closeable {

    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private boolean isConnected = false;
    private String loggedInUsername = null;
    private String noconnect = "Not connected or not logged in.";

    private String userPassword;

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
             return new ServerResponse(false, noconnect, null);
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
             return new ServerResponse(false, noconnect, null);
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
            throw new IllegalStateException(noconnect);
        }
        if (fileToSend == null || !fileToSend.exists()) {
            throw new IllegalArgumentException("Invalid file selected for upload.");
        }
        if (userPassword == null || userPassword.isEmpty()) {
            throw new IllegalStateException("User password is not set. Cannot create encryption key.");
        }

        // Tạo file tạm để chứa dữ liệu đã mã hóa
        File encryptedTempFile = File.createTempFile("enc_", ".tmp");

        try {
            // --- 1. Mã hóa file trước khi upload ---
            SecretKey key = deriveKeyFromPassword(userPassword);
            System.out.println("UPLOAD KEY: " + java.util.Base64.getEncoder().encodeToString(key.getEncoded())); // In key
            encryptFile(fileToSend, encryptedTempFile, key);

            // --- 2. Gửi file đã mã hóa ---
            // Đổi tên file gửi đi để server biết đây là file gốc
            // (Server sẽ lưu với tên gốc, không phải tên file tạm)
            String originalFilename = fileToSend.getName();
            return uploadEncryptedFile(encryptedTempFile, originalFilename, progressCallback);

        } finally {
            // --- 3. Luôn xóa file tạm sau khi upload xong (dù thành công hay thất bại) ---
            if (encryptedTempFile != null && encryptedTempFile.exists()) {
                encryptedTempFile.delete();
            }
        }
    }

    // Dán để THAY THẾ hoàn toàn phương thức uploadFile cũ
    /*@SuppressWarnings("exports")
    public ServerResponse uploadFile(File fileToSend, BiConsumer<Long, Long> progressCallback)
            throws Exception {

        if (!isConnected || loggedInUsername == null) {
            throw new IllegalStateException(noconnect);
        }
        if (fileToSend == null || !fileToSend.exists()) {
            throw new IllegalArgumentException("Invalid file selected for upload.");
        }
        
        // THAY ĐỔI: Không còn mã hóa, không còn file tạm.
        // Chúng ta sẽ gọi trực tiếp logic gửi file với file gốc.
        return uploadEncryptedFile(fileToSend, fileToSend.getName(), progressCallback);
    }*/
    
    private ServerResponse uploadEncryptedFile(File encryptedFile, String originalFilename, BiConsumer<Long, Long> progressCallback)
        throws Exception {

        long fileSize = encryptedFile.length();

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.log(java.util.logging.Level.INFO, "Client: Sending UPLOAD_FILE request for {0}", originalFilename);
        }
        // 1. Gửi yêu cầu UPLOAD_FILE kèm metadata ban đầu (tên gốc, kích thước file đã mã hóa)
        this.oos.writeObject(new ServerRequest(RequestType.UPLOAD_FILE, new FileMetadata(originalFilename, fileSize)));
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

        try (InputStream fis = new FileInputStream(encryptedFile)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] dataToSend = java.util.Arrays.copyOf(buffer, bytesRead);
                // isLast không còn cần thiết vì server sẽ đọc đến hết stream
                FileTransferPacket packet = new FileTransferPacket(originalFilename, dataToSend, 0, 0, fileSize, (totalSent + dataToSend.length) >= fileSize);

                this.oos.writeObject(packet);
                this.oos.flush();

                totalSent += dataToSend.length;

                if (progressCallback != null) {
                    progressCallback.accept(totalSent, fileSize);
                }
            }
        }

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.log(java.util.logging.Level.INFO, "Client: Finished sending file data for {0}, total sent: {1}", new Object[]{originalFilename, totalSent});
        }

        // 4. Nhận phản hồi kết quả upload cuối cùng từ Server
        Object finalReceived = this.ois.readObject();
        if (!(finalReceived instanceof ServerResponse)) {
            throw new IOException("Received unexpected object from server after file data sent.");
        }
        return (ServerResponse) finalReceived;
    }

    @SuppressWarnings("exports")
    public ServerResponse deleteFile(String filename) {
        if (!isConnected || loggedInUsername == null) {
            return new ServerResponse(false, "Not connected or not logged in.", null);
        }
        ServerRequest request = new ServerRequest(RequestType.DELETE_FILE, filename);
        return sendAndReceive(request);
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
    @SuppressWarnings("exports")
    public FileMetadata downloadFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback)
            throws Exception {

        if (userPassword == null || userPassword.isEmpty()) {
            throw new IllegalStateException("User password is not set. Cannot create decryption key.");
        }

        // Tạo file tạm để chứa dữ liệu mã hóa tải về
        File encryptedTempFile = File.createTempFile("enc_down_", ".tmp");

        try {
            // --- 1. Tải file mã hóa về file tạm ---
            FileMetadata meta = downloadEncryptedFile(filenameToDownload, encryptedTempFile, progressCallback);

            // --- 2. Giải mã từ file tạm ra file đích ---
            SecretKey key = deriveKeyFromPassword(userPassword);
            System.out.println("DOWNLOAD KEY: " + java.util.Base64.getEncoder().encodeToString(key.getEncoded())); // In key
            decryptFile(encryptedTempFile, saveFile, key);

            // --- 3. Trả về metadata gốc ---
            return meta;

        } finally {
            // --- 4. Luôn xóa file tạm sau khi hoàn tất ---
            if (encryptedTempFile != null && encryptedTempFile.exists()) {
                encryptedTempFile.delete();
            }
        }
    }

        // Dán để THAY THẾ hoàn toàn phương thức downloadFile cũ
    /*@SuppressWarnings("exports")
    public FileMetadata downloadFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback)
            throws Exception {

        // THAY ĐỔI: Không còn kiểm tra mật khẩu, không còn file tạm, không còn bước giải mã.
        // Logic bây giờ chỉ đơn giản là tải file đã mã hóa (trong trường hợp này là file gốc) về thẳng nơi lưu.
        return downloadEncryptedFile(filenameToDownload, saveFile, progressCallback);
    }
    */
    @SuppressWarnings({"LoggerStringConcat", "exports"})
    private FileMetadata downloadEncryptedFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback)
            throws Exception {

        validateDownloadParams(filenameToDownload, saveFile);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServerClient.class.getName());
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info("Client: Sending DOWNLOAD_FILE request for " + filenameToDownload);
        }

        // Gửi yêu cầu tải file
        this.oos.writeObject(new ServerRequest(RequestType.DOWNLOAD_FILE, filenameToDownload));
        this.oos.flush();

        // Nhận phản hồi ban đầu và metadata
        ServerResponse initialResponse = receiveDownloadResponse(saveFile);
        FileMetadata downloadedFileMetadata = (FileMetadata) initialResponse.getData();
        long expectedSize = downloadedFileMetadata.getFileSize();

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.log(java.util.logging.Level.INFO, "Client: Server is ready to send encrypted file: {0} ({1} bytes)",
                    new Object[]{downloadedFileMetadata.getFilename(), expectedSize});
        }

        // Nhận dữ liệu file và lưu vào file tạm
        long totalReceived = receiveFileData(saveFile, expectedSize, progressCallback, logger);

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(String.format("Client: Finished receiving encrypted file data for %s, total received: %d",
                    filenameToDownload, totalReceived));
        }

        // Xác thực kích thước file đã tải
        verifyFileSize(totalReceived, expectedSize, saveFile, filenameToDownload, logger);

        return downloadedFileMetadata;
    }

    private void validateDownloadParams(String filenameToDownload, File saveFile) {
        if (!isConnected || loggedInUsername == null) {
            throw new IllegalStateException(noconnect);
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

    // Dán đoạn code này để THAY THẾ hoàn toàn phương thức receiveFileData cũ trong serverclient.java

    private long receiveFileData(File saveFile, long expectedSize, BiConsumer<Long, Long> progressCallback,
        java.util.logging.Logger logger) throws FileTransferException {

        long totalReceived = 0;
        // Sử dụng try-with-resources để đảm bảo FileOutputStream luôn được đóng
        try (OutputStream fos = Files.newOutputStream(saveFile.toPath(), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // =======================================================================
            // THAY ĐỔI QUAN TRỌNG NHẤT LÀ Ở ĐÂY
            // Thay vì `while(true)`, chúng ta lặp cho đến khi nhận đủ số byte mong đợi.
            // Đây là cách tiếp cận mạnh mẽ và đáng tin cậy hơn nhiều.
            // =======================================================================
            while (totalReceived < expectedSize) {
                Object receivedObject = this.ois.readObject(); 

                // Kiểm tra để đảm bảo chúng ta nhận đúng loại đối tượng
                if (!(receivedObject instanceof FileTransferPacket)) {
                    String errorMessage = "Received unexpected data type from server during download. Expected FileTransferPacket, but got "
                                        + (receivedObject != null ? receivedObject.getClass().getName() : "null");
                    logger.severe("Client: " + errorMessage);
                    throw new FileTransferException(errorMessage);
                }

                FileTransferPacket packet = (FileTransferPacket) receivedObject;
                byte[] data = packet.getData();

                if (data != null && data.length > 0) {
                    // Kiểm tra để không ghi vượt quá kích thước mong đợi
                    int bytesToWrite = (int) Math.min(data.length, expectedSize - totalReceived);
                    fos.write(data, 0, bytesToWrite);
                    totalReceived += bytesToWrite;

                    if (progressCallback != null) {
                        progressCallback.accept(totalReceived, expectedSize);
                    }
                }

                // Cờ isLastPacket bây giờ không còn cần thiết để điều khiển vòng lặp nữa.
                // Vòng lặp sẽ tự động kết thúc khi totalReceived == expectedSize.
            }
        } catch (IOException | ClassNotFoundException e) {
            // Bọc các exception cấp thấp hơn bằng FileTransferException để làm rõ hợp đồng của phương thức
            throw new FileTransferException("A network or data-related error occurred during file reception.", e);
        }
        
        // Trả về tổng số byte đã nhận để có thể xác thực ở bước tiếp theo
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

    // Sửa hàm này
    private SecretKey deriveKeyFromPassword(String password) throws Exception {
        // CẢNH BÁO: Trong ứng dụng thực tế, salt nên được lưu trữ an toàn,
        // không nên hardcode. Nhưng dùng salt cố định vẫn tốt hơn nhiều
        // so với việc dùng chính mật khẩu làm salt.
        byte[] salt = "a-secure-fixed-salt-for-this-app".getBytes("UTF-8");
        int iterationCount = 65536; // Số vòng lặp, tiêu chuẩn
        int keyLength = 256;       // Dùng khóa 256-bit cho an toàn hơn

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    // Sửa hàm này
    private void encryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        // 1. Tạo một Initialization Vector (IV) ngẫu nhiên
        byte[] iv = new byte[16]; // AES/CBC sử dụng IV 16 byte
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        // 2. Khởi tạo Cipher để mã hóa
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParameterSpec);

        try (FileOutputStream fos = new FileOutputStream(outputFile);
            FileInputStream fis = new FileInputStream(inputFile)) {

            // 3. GHI IV VÀO ĐẦU FILE OUTPUT. Bên giải mã sẽ cần nó.
            fos.write(iv);

            // 4. Dùng CipherOutputStream để mã hóa và ghi phần còn lại của file
            try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    cos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // Sửa hàm này
    private void decryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputFile)) {

            // 1. ĐỌC 16 BYTE ĐẦU TIÊN ĐỂ LẤY LẠI IV
            byte[] iv = new byte[16];
            int ivRead = fis.read(iv);
            if (ivRead < 16) {
                throw new IllegalArgumentException("Invalid encrypted file: Missing IV.");
            }
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // 2. Khởi tạo Cipher để giải mã
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, ivParameterSpec);

            // 3. Dùng CipherInputStream để đọc và giải mã phần dữ liệu còn lại
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                CipherInputStream cis = new CipherInputStream(fis, cipher)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    public void setUserPassword(String password) {
        this.userPassword = password;
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