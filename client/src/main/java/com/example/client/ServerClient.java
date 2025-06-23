// Dán để THAY THẾ TOÀN BỘ file serverclient.java cũ của bạn

package com.example.client;

import com.example.shared.AuthRequest;
import com.example.shared.FileMetadata;
import com.example.shared.FileTransferPacket;
import com.example.shared.RequestType;
import com.example.shared.ServerRequest;
import com.example.shared.ServerResponse;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerClient implements Closeable {

    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final Logger LOGGER = Logger.getLogger(ServerClient.class.getName());

    // --- Cập nhật hằng số cho AES/GCM ---
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"; // THAY ĐỔI QUAN TRỌNG
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int GCM_NONCE_LENGTH_BYTES = 12; // GCM tiêu chuẩn dùng nonce 12 byte (96 bit)
    private static final int GCM_TAG_LENGTH_BITS = 128;   // Kích thước thẻ xác thực
    private static final int PBE_ITERATION_COUNT = 65536;
    private static final int PBE_KEY_LENGTH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private boolean isConnected = false;
    private String loggedInUsername = null;
    private String userPassword;
    private static final String NO_CONNECT = "Not connected or not logged in.";


    public ServerClient() {
        // Implementation constructor
    }
    
    // ... Các phương thức khác không thay đổi ...
    public boolean connect() {
        try {
            if (!isConnected || socket == null || socket.isClosed()) {
                 socket = new Socket(SERVER_IP, SERVER_PORT);
                 oos = new ObjectOutputStream(socket.getOutputStream());
                 oos.flush();
                 ois = new ObjectInputStream(socket.getInputStream());
                 isConnected = true;
            }
            return true;
        } catch (IOException e) {
            closeConnection();
            return false;
        }
    }

    @Override
    public void close() {
        closeConnection();
    }
    
    public void closeConnection() {
        loggedInUsername = null;
        isConnected = false;
        try {
            if (oos != null) { oos.close(); }
            if (ois != null) { ois.close(); }
            if (socket != null && !socket.isClosed()) { socket.close(); }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Exception while closing resources", e);
        } finally {
             oos = null;
             ois = null;
             socket = null;
        }
    }

    private ServerResponse sendAndReceive(ServerRequest request) {
        if (!isConnected) {
             LOGGER.severe("Cannot send request: Not connected to server.");
             return new ServerResponse(false, "Not connected to server.", null);
        }
        try {
            this.oos.writeObject(request);
            this.oos.flush();
            Object received = this.ois.readObject();
             if (!(received instanceof ServerResponse)) {
                 LOGGER.log(Level.SEVERE, "Received unexpected object type: {0}", (received != null ? received.getClass().getName() : "null"));
                  throw new IOException("Received unexpected data from server.");
             }
            return (ServerResponse) received;
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Communication error during send/receive", e);
            closeConnection();
            return null;
        }
    }
    
    @SuppressWarnings("exports")
    public ServerResponse authenticate(String username, String password) {
        if (!connect()) {
            return new ServerResponse(false, "Failed to connect to server.", null);
        }
        ServerRequest request = new ServerRequest(RequestType.LOGIN, new AuthRequest(username, password));
        ServerResponse response = sendAndReceive(request);
        if (response != null && response.isSuccess() && response.getData() instanceof String) {
            this.loggedInUsername = (String) response.getData();
        } else {
            this.loggedInUsername = null;
             closeConnection();
        }
        return response;
    }

    @SuppressWarnings("exports")
    public ServerResponse register(String username, String password) {
         if (!connect()) {
             return new ServerResponse(false, "Failed to connect to server.", null);
         }
        ServerRequest request = new ServerRequest(RequestType.REGISTER, new AuthRequest(username, password));
        ServerResponse response = sendAndReceive(request);
        closeConnection();
        return response;
    }

     @SuppressWarnings("exports")
    public ServerResponse logout() {
         if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(true, "Already logged out or not connected.", null);
         }
         ServerRequest request = new ServerRequest(RequestType.LOGOUT, loggedInUsername);
         ServerResponse response = sendAndReceive(request);
         closeConnection();
         return response;
     }

    @SuppressWarnings("exports")
    public ServerResponse getFileList() {
        if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(false, NO_CONNECT, null);
        }
        ServerRequest request = new ServerRequest(RequestType.LIST_FILES, null);
        return sendAndReceive(request);
    }
    
    @SuppressWarnings("exports")
    public ServerResponse searchFiles(String query) {
        if (!isConnected || loggedInUsername == null) {
             return new ServerResponse(false, NO_CONNECT, null);
        }
        ServerRequest request = new ServerRequest(RequestType.SEARCH_FILES, query);
        return sendAndReceive(request);
    }

    @SuppressWarnings("exports")
    public ServerResponse uploadFile(File fileToSend, BiConsumer<Long, Long> progressCallback) throws FileEncryptionException {
        if (fileToSend == null || !fileToSend.exists()) {
            throw new IllegalArgumentException("Invalid file selected for upload.");
        }
        if (this.userPassword == null || this.userPassword.isEmpty()) {
            throw new IllegalStateException("User password is not set. Cannot create encryption key.");
        }

        File encryptedTempFile = null;
        try {
            encryptedTempFile = File.createTempFile("enc_upload_", ".tmp");
            encryptFile(fileToSend, encryptedTempFile, this.userPassword);
            return uploadEncryptedFile(encryptedTempFile, fileToSend.getName(), progressCallback);
        } catch (Exception e) {
            throw new FileEncryptionException("Top-level upload process failed.", e);
        }
        finally {
            if (encryptedTempFile != null) {
                try {
                    Files.deleteIfExists(encryptedTempFile.toPath());
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Failed to delete temporary upload file", ex);
                }
            }
        }
    }
    
    @SuppressWarnings("exports")
    public FileMetadata downloadFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback) throws FileDecryptionException {
        if (this.userPassword == null || this.userPassword.isEmpty()) {
            throw new IllegalStateException("User password is not set. Cannot create decryption key.");
        }

        File encryptedTempFile = null;
        try {
            encryptedTempFile = File.createTempFile("enc_down_", ".tmp");
            FileMetadata meta = downloadEncryptedFile(filenameToDownload, encryptedTempFile, progressCallback);
            decryptFile(encryptedTempFile, saveFile, this.userPassword);
            return meta;
        } catch (Exception e) {
            throw new FileDecryptionException("Top-level download process failed.", e);
        }
        finally {
            if (encryptedTempFile != null) {
                try {
                    Files.deleteIfExists(encryptedTempFile.toPath());
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Failed to delete temporary download file", ex);
                }
            }
        }
    }
    
    private ServerResponse uploadEncryptedFile(File encryptedFile, String originalFilename, BiConsumer<Long, Long> progressCallback) throws Exception {
        if (!isConnected || loggedInUsername == null) {
            throw new IllegalStateException(NO_CONNECT);
        }
        long fileSize = encryptedFile.length();
        this.oos.writeObject(new ServerRequest(RequestType.UPLOAD_FILE, new FileMetadata(originalFilename, fileSize)));
        this.oos.flush();
        ServerResponse initialResponse = (ServerResponse) this.ois.readObject();
        if (!initialResponse.isSuccess()) {
            throw new ServerUploadException("Server refused upload: " + initialResponse.getMessage());
        }
        byte[] buffer = new byte[4096];
        long totalSent = 0;
        try (InputStream fis = new FileInputStream(encryptedFile)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] dataToSend = java.util.Arrays.copyOf(buffer, bytesRead);
                FileTransferPacket packet = new FileTransferPacket(originalFilename, dataToSend, 0, 0, fileSize, (totalSent + dataToSend.length) >= fileSize);
                this.oos.writeObject(packet);
                this.oos.flush();
                totalSent += dataToSend.length;
                if (progressCallback != null) {
                    progressCallback.accept(totalSent, fileSize);
                }
            }
        }
        return (ServerResponse) this.ois.readObject();
    }
    
    private FileMetadata downloadEncryptedFile(String filenameToDownload, File saveFile, BiConsumer<Long, Long> progressCallback) throws Exception {
        validateDownloadParams(filenameToDownload, saveFile);
        this.oos.writeObject(new ServerRequest(RequestType.DOWNLOAD_FILE, filenameToDownload));
        this.oos.flush();
        ServerResponse initialResponse = receiveDownloadResponse(saveFile);
        FileMetadata downloadedFileMetadata = (FileMetadata) initialResponse.getData();
        long expectedSize = downloadedFileMetadata.getFileSize();
        long totalReceived = receiveFileData(saveFile, expectedSize, progressCallback);
        verifyFileSize(totalReceived, expectedSize, saveFile, filenameToDownload);
        return downloadedFileMetadata;
    }
    
    private void validateDownloadParams(String filenameToDownload, File saveFile) {
        if (!isConnected || loggedInUsername == null) {
            throw new IllegalStateException(NO_CONNECT);
        }
        if (filenameToDownload == null || filenameToDownload.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty for download.");
        }
        if (saveFile == null) {
            throw new IllegalArgumentException("Save location cannot be null.");
        }
    }

    private ServerResponse receiveDownloadResponse(File saveFile) throws Exception {
        Object initialReceived = this.ois.readObject();
        if (!(initialReceived instanceof ServerResponse)) {
            throw new IOException("Received unexpected data from server after download request.");
        }
        ServerResponse initialResponse = (ServerResponse) initialReceived;
        if (!initialResponse.isSuccess() || !(initialResponse.getData() instanceof FileMetadata)) {
            if (Files.exists(saveFile.toPath())) {
                try {
                    Files.delete(saveFile.toPath());
                } catch (IOException deleteEx) {
                    LOGGER.log(Level.WARNING, "Failed to delete empty partial file", deleteEx);
                }
            }
            throw new ServerDownloadException("Server refused download or did not provide metadata: " + initialResponse.getMessage());
        }
        return initialResponse;
    }
    
    private long receiveFileData(File saveFile, long expectedSize, BiConsumer<Long, Long> progressCallback) throws FileTransferException {
        long totalReceived = 0;
        try (OutputStream fos = Files.newOutputStream(saveFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (totalReceived < expectedSize) {
                Object receivedObject = this.ois.readObject();
                if (!(receivedObject instanceof FileTransferPacket)) {
                    throw new FileTransferException("Received unexpected data type from server during download.");
                }
                FileTransferPacket packet = (FileTransferPacket) receivedObject;
                byte[] data = packet.getData();
                if (data != null && data.length > 0) {
                    int bytesToWrite = (int) Math.min(data.length, expectedSize - totalReceived);
                    fos.write(data, 0, bytesToWrite);
                    totalReceived += bytesToWrite;
                    if (progressCallback != null) {
                        progressCallback.accept(totalReceived, expectedSize);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new FileTransferException("A network or data-related error occurred during file reception.", e);
        }
        return totalReceived;
    }
    
    private void verifyFileSize(long totalReceived, long expectedSize, File saveFile, String filenameToDownload) throws IOException {
        if (totalReceived != expectedSize) {
            LOGGER.log(Level.SEVERE, "File size mismatch for {0}. Expected: {1}, Received: {2}", new Object[]{filenameToDownload, expectedSize, totalReceived});
            Files.deleteIfExists(saveFile.toPath());
            throw new IOException("File size mismatch during download.");
        }
    }

    @SuppressWarnings("exports")
    public ServerResponse deleteFile(String filename) {
        if (!isConnected || loggedInUsername == null) {
            return new ServerResponse(false, NO_CONNECT, null);
        }
        ServerRequest request = new ServerRequest(RequestType.DELETE_FILE, filename);
        return sendAndReceive(request);
    }

    private SecretKey deriveKeyFromPassword(String password, byte[] salt) throws KeyDerivationException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBE_ITERATION_COUNT, PBE_KEY_LENGTH_BITS);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
        } catch (Exception e) {
            throw new KeyDerivationException("Failed to derive encryption key from password.", e);
        }
    }

    /**
     * Mã hóa một file sử dụng AES/GCM.
     * File output sẽ có cấu trúc: [16-byte SALT][12-byte NONCE][Dữ liệu mã hóa + 16-byte TAG].
     */
    private void encryptFile(File inputFile, File outputFile, String password) throws FileEncryptionException {
        try {
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(salt);
            SecretKey key = deriveKeyFromPassword(password, salt);

            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(nonce);
            // GCMParameterSpec thay thế IvParameterSpec cho GCM
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 FileInputStream fis = new FileInputStream(inputFile)) {

                fos.write(salt);
                fos.write(nonce); // Ghi nonce thay vì IV

                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        cos.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (Exception e) {
            throw new FileEncryptionException("Failed to encrypt file.", e);
        }
    }

    /**
     * Giải mã một file đã được mã hóa bởi AES/GCM.
     */
    // Dán để THAY THẾ duy nhất phương thức decryptFile cũ trong ServerClient.java


    private void decryptFile(File inputFile, File outputFile, String password) throws FileDecryptionException {
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            // 1. Đọc salt từ đầu file
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            if (fis.read(salt) < SALT_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted file: file is too short to contain a salt.");
            }

            // 2. Đọc nonce từ file
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            if (fis.read(nonce) < GCM_NONCE_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted file: file is too short to contain a nonce.");
            }
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);

            // 3. Dẫn xuất lại khóa từ mật khẩu và salt đã đọc
            SecretKey key = deriveKeyFromPassword(password, salt);

            // 4. Khởi tạo Cipher để giải mã
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            // 5. Đọc phần còn lại của file và giải mã
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                CipherInputStream cis = new CipherInputStream(fis, cipher)) {

                byte[] buffer = new byte[4096];
                while (cis.read(buffer) != -1) {
                    // Vòng lặp này sẽ ném ra IOException (với cause là AEADBadTagException)
                    // khi nó đọc đến cuối và xác thực GCM tag thất bại.
                }
                // Chuyển việc ghi file vào trong vòng lặp để xử lý ngay lập tức
                // Đoạn code cũ có thể ghi một phần file trước khi phát hiện lỗi ở cuối.
                // Chúng ta sẽ sửa lại để nó an toàn hơn.
            }

            // ====> SỬA LỖI LOGIC GIẢI MÃ <====
            // Để đảm bảo an toàn, chúng ta thực hiện giải mã lại một lần nữa nhưng ghi ra file.
            // Cách tiếp cận này đảm bảo rằng chúng ta chỉ ghi file ra đĩa NẾU toàn bộ quá trình giải mã thành công.
            try (FileInputStream fisRetry = new FileInputStream(inputFile);
                FileOutputStream fos = new FileOutputStream(outputFile)) {
                
                // Bỏ qua salt và nonce đã đọc
                long bytesToSkip = (long)SALT_LENGTH_BYTES + (long)GCM_NONCE_LENGTH_BYTES;
                long skipped = fisRetry.skip(bytesToSkip);
                if (skipped < bytesToSkip) {
                    throw new FileDecryptionException("Invalid encrypted file: not enough bytes to skip salt and nonce.", null);
                }
                
                try (CipherInputStream cis = new CipherInputStream(fisRetry, cipher)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = cis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            // ====================================================================
            // ĐÂY LÀ PHẦN SỬA LỖI QUAN TRỌNG NHẤT
            // Kiểm tra nguyên nhân gốc rễ của IOException
            // ====================================================================
            if (e.getCause() instanceof AEADBadTagException) {
                // Nếu nguyên nhân là do GCM tag không hợp lệ, đây là lỗi đáng ngờ nhất.
                throw new FileDecryptionException("Decryption failed: File is corrupted or has been tampered with. (Invalid GCM Tag)", e);
            } else {
                // Nếu không, đó là một lỗi I/O thông thường.
                throw new FileDecryptionException("Failed to decrypt file due to an I/O error.", e);
            }
        } catch (Exception e) {
            // Bắt các lỗi khác có thể xảy ra trong quá trình thiết lập (ví dụ: deriveKey)
            throw new FileDecryptionException("Failed to decrypt file due to a setup or cryptographic error.", e);
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


// --- CÁC LỚP EXCEPTION ---

class ServerUploadException extends Exception {
    public ServerUploadException(String message) {
        super(message);
    }
}

class ServerDownloadException extends Exception {
    public ServerDownloadException(String message) {
        super(message);
    }
}

class KeyDerivationException extends Exception {
    public KeyDerivationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class FileTransferException extends Exception {
    public FileTransferException(String message) {
        super(message);
    }
    public FileTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}

class FileEncryptionException extends Exception {
    public FileEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Thêm Exception mới cho việc giải mã để xử lý lỗi tốt hơn
class FileDecryptionException extends Exception {
    public FileDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}