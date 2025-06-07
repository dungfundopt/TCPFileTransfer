package com.example.server; // Đảm bảo đúng package

import java.io.IOException; // Vẫn cần AuthRequest nếu dùng cấu trúc cũ
import java.io.InputStream; // Import FileMetadata
import java.io.ObjectInputStream; // Import FileListResponse
import java.io.ObjectOutputStream; // Import FileTransferPacket
import java.io.OutputStream; // Import RequestType
import java.net.Socket; // Import ServerRequest
import java.nio.file.Files; // Import ServerResponse
import java.nio.file.Path; // Cần cho file I/O
import java.nio.file.Paths;
import java.time.LocalDateTime; // Cần cho thao tác file dễ dàng
import java.util.List;

import com.example.shared.AuthRequest;
import com.example.shared.FileListResponse;
import com.example.shared.FileMetadata;
import com.example.shared.FileTransferPacket;
import com.example.shared.RequestType;
import com.example.shared.ServerRequest;
import com.example.shared.ServerResponse;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private DatabaseHandler dbHandler;
    // Giả định Server chỉ lưu file vào thư mục 'uploads' trong cùng thư mục chạy Server JAR
    private static final Path UPLOAD_DIR = Paths.get("uploads");
    private String loggedInUsername = null; // Lưu username của client đã đăng nhập

    public ClientHandler(Socket socket, DatabaseHandler dbHandler) {
        this.clientSocket = socket;
        this.dbHandler = dbHandler;
         // Đảm bảo thư mục uploads tồn tại
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            
            // Server có thể cần thoát hoặc thông báo lỗi nghiêm trọng hơn
        }
    }

    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() {
        try (
            ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
        ) {
            while (clientSocket.isConnected() && !clientSocket.isClosed()) {
                ServerRequest request = (ServerRequest) ois.readObject();
                ServerResponse response = handleRequest(request, ois, oos);
                if (response != null) {
                    oos.writeObject(response);
                    oos.flush();
                }
                if (shouldTerminate(request)) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // e.printStackTrace(); // In chi tiết lỗi nếu cần debug
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ServerResponse handleRequest(ServerRequest request, ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
        switch (request.getType()) {
            case LOGIN, REGISTER -> {
                return handleAuthRequest(request);
            }
            case LIST_FILES -> {
                return handleListFilesRequest();
            }
            case SEARCH_FILES -> {
                return handleSearchFilesRequest(request);
            }
            case UPLOAD_FILE -> {
                return handleUploadFileRequest(request, ois, oos);
            }
            case DOWNLOAD_FILE -> {
                return handleDownloadFileRequest(request, oos);
            }
            case LOGOUT -> {
                loggedInUsername = null;
                return new ServerResponse(true, "Logged out successfully.", null);
            }
            default -> {
                return new ServerResponse(false, "Unknown request type.", null);
            }
        }
    }

    private boolean shouldTerminate(ServerRequest request) {
        return request.getType() == RequestType.LOGOUT ||
               ((request.getType() != RequestType.LOGIN && request.getType() != RequestType.REGISTER)
                && loggedInUsername == null);
    }

    private ServerResponse handleAuthRequest(ServerRequest request) {
        if (loggedInUsername == null && request.getData() instanceof AuthRequest) {
            AuthRequest authRequest = (AuthRequest) request.getData();
            if (request.getType() == RequestType.LOGIN) {
                boolean success = dbHandler.verifyUser(authRequest.getUsername(), authRequest.getPassword());
                if (success) {
                    loggedInUsername = authRequest.getUsername();
                    return new ServerResponse(true, "Login successful!", loggedInUsername);
                } else {
                    return new ServerResponse(false, "Invalid username or password.", null);
                }
            } else { // REGISTER
                boolean success = dbHandler.addUser(authRequest.getUsername(), authRequest.getPassword());
                if (success) {
                    return new ServerResponse(true, "Registration successful!", null);
                } else {
                    return new ServerResponse(false, "Registration failed. Username might already exist.", null);
                }
            }
        } else {
            return new ServerResponse(false, "Already logged in or invalid auth request.", null);
        }
    }

    private ServerResponse handleListFilesRequest() {
        if (loggedInUsername != null) {
            List<FileMetadata> fileList = dbHandler.getAllFileMetadata();
            return new ServerResponse(true, "File list retrieved.", new FileListResponse(fileList));
        } else {
            return new ServerResponse(false, "Authentication required.", null);
        }
    }

    private ServerResponse handleSearchFilesRequest(ServerRequest request) {
        if (loggedInUsername != null && request.getData() instanceof String) {
            String query = (String) request.getData();
            List<FileMetadata> fileList = dbHandler.searchFileMetadata(query);
            return new ServerResponse(true, "Search results retrieved.", new FileListResponse(fileList));
        } else {
            return new ServerResponse(false, "Authentication required or invalid search query.", null);
        }
    }

    private ServerResponse handleUploadFileRequest(ServerRequest request, ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
        if (loggedInUsername != null && request.getData() instanceof FileMetadata) {
            FileMetadata fileMetadata = (FileMetadata) request.getData();
            Path filePath = UPLOAD_DIR.resolve(fileMetadata.getFilename());
            if (dbHandler.getFileMetadataByName(fileMetadata.getFilename()) != null) {
                return new ServerResponse(false, "File with this name already exists.", null);
            } else {
                // Ready to receive file
                oos.writeObject(new ServerResponse(true, "Ready to receive file.", null));
                oos.flush();
                receiveFile(ois, filePath, fileMetadata.getFileSize());
                FileMetadata completeMetadata = new FileMetadata(
                        fileMetadata.getFilename(),
                        fileMetadata.getFileSize(),
                        loggedInUsername,
                        LocalDateTime.now()
                );
                boolean metadataSaved = dbHandler.addFileMetadata(completeMetadata);
                return new ServerResponse(metadataSaved,
                        metadataSaved ? "File uploaded and metadata saved." : "File uploaded, but failed to save metadata.",
                        null);
            }
        } else {
            return new ServerResponse(false, "Authentication required or invalid upload request.", null);
        }
    }

    private ServerResponse handleDownloadFileRequest(ServerRequest request, ObjectOutputStream oos) throws IOException {
        if (loggedInUsername != null && request.getData() instanceof String) {
            String filenameToDownload = (String) request.getData();
            Path filePath = UPLOAD_DIR.resolve(filenameToDownload);
            FileMetadata fileMetadata = dbHandler.getFileMetadataByName(filenameToDownload);
            if (fileMetadata != null && Files.exists(filePath)) {
                oos.writeObject(new ServerResponse(true, "Ready to send file.", fileMetadata));
                oos.flush();
                sendFile(filePath, fileMetadata.getFilename(), fileMetadata.getFileSize(), oos);
                return null;
            } else {
                return new ServerResponse(false, "File not found on server or in database.", null);
            }
        } else {
            return new ServerResponse(false, "Authentication required or invalid download request.", null);
        }
    }

    // Phương thức để nhận file từ client
    private void receiveFile(ObjectInputStream ois, Path filePath, long expectedSize) throws IOException, ClassNotFoundException {
        
        long receivedSize = 0;
        try (OutputStream fos = Files.newOutputStream(filePath)) { // Ghi dữ liệu vào file
             FileTransferPacket packet;
             while ((packet = (FileTransferPacket) ois.readObject()) != null) {
                  if (packet.getData() != null) {
                     fos.write(packet.getData());
                     receivedSize += packet.getData().length;
                     
                  }
                 if (packet.isLastPacket()) {
                     break; // Kết thúc nhận file khi gặp gói cuối cùng
                 }
             }
         } // fos sẽ tự đóng

        
        
        if (receivedSize != expectedSize) {
             
             // Xóa file đã nhận nếu kích thước sai? Hoặc xử lý lỗi khác
             
        }
    }

    // Phương thức để gửi file đến client
    private void sendFile(Path filePath, String filename, long fileSize, ObjectOutputStream oos) throws IOException {
         
         long totalSent = 0;
         int sequence = 0;
         int bufferSize = 4096; // Kích thước gói
         long totalPackets = (fileSize + bufferSize - 1) / bufferSize; // Tổng số gói

        try (InputStream fis = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                 byte[] dataToSend = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead); // Đảm bảo chỉ gửi số byte đã đọc

                 boolean isLast = (totalSent + bytesRead) >= fileSize; // Kiểm tra xem gói này có phải gói cuối không

                 FileTransferPacket packet = new FileTransferPacket(
                     filename,
                     dataToSend,
                     sequence++, // Số thứ tự gói tăng dần
                     (int)totalPackets, // Tổng số gói (cần cast sang int, cẩn thận với file rất lớn > 2GB/BufferSize)
                     fileSize,
                     isLast
                 );

                 oos.writeObject(packet); // Gửi gói
                 oos.flush(); // Đảm bảo gói được gửi ngay lập tức

                 totalSent += bytesRead;

                 // Thêm delay nhỏ để tránh gửi quá nhanh gây tắc nghẽn
                 
            }
        } // fis sẽ tự đóng

        
        // Gói cuối cùng đã được gửi với cờ isLastPacket = true
        // Có thể gửi thêm một ServerResponse báo thành công nếu cần xác nhận từ client
    }

     
}