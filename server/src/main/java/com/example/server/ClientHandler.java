
package com.example.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import com.example.shared.AuthRequest;
import com.example.shared.FileListResponse;
import com.example.shared.FileMetadata;
import com.example.shared.FileTransferPacket;
import com.example.shared.RequestType;
import com.example.shared.ServerRequest;
import com.example.shared.ServerResponse;
import java.util.logging.*;
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private DatabaseHandler dbHandler;
    private static final Path UPLOAD_DIR = Paths.get("uploads");
    private String loggedInUsername = null;
    public static final String AUTHENTICATION_REQUIRED = "Authentication required.";
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    public ClientHandler(Socket socket, DatabaseHandler dbHandler) {
        this.clientSocket = socket;
        this.dbHandler = dbHandler;
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            logger.severe("An error directory occurred");
        }
    }

    @Override
    public void run() {
        try (ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

            boolean running = true;
            while (clientSocket.isConnected() && !clientSocket.isClosed() && running) {
                ServerRequest request = readRequest(ois);
                if (request == null) {
                    running = false;
                } else {
                    running = !handleRequest(request, ois, oos);
                }
            }
        } catch (IOException e) {
            logger.info("Client " + (loggedInUsername != null ? loggedInUsername : clientSocket.getInetAddress()) + " disconnected.");
        } finally {
            closeSocket();
        }
    }

    private ServerRequest readRequest(ObjectInputStream ois) {
        try {
            return (ServerRequest) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Failed to read request: " + e.getMessage());
            return null;
        }
    }

    private boolean handleRequest(ServerRequest request, ObjectInputStream ois, ObjectOutputStream oos) {
        try {
            switch (request.getType()) {
                case UPLOAD_FILE:
                    handleUploadFileRequest(request, ois, oos);
                    break;
                case DOWNLOAD_FILE:
                    handleDownloadFileRequest(request, oos);
                    break;
                default:
                    handleDefaultRequest(request, oos);
                    break;
            }
        } catch (Exception e) {
            logger.severe("Error handling request: " + e.getMessage());
            try {
                oos.writeObject(new ServerResponse(false, "Server error: " + e.getMessage(), null));
                oos.flush();
            } catch (IOException ioException) {
                logger.severe("Failed to send error response to client: " + ioException.getMessage());
            }
        }
        return request.getType() == RequestType.LOGOUT || (loggedInUsername == null && requiresAuth(request.getType()));
    }

    private void handleDefaultRequest(ServerRequest request, ObjectOutputStream oos) throws IOException {
        ServerResponse response = handleSimpleRequest(request);
            oos.writeObject(response);
            oos.flush();
    }

    
    private ServerResponse handleSimpleRequest(ServerRequest request) {
        switch (request.getType()) {
            
            case LOGIN, REGISTER:
                return handleAuthRequest(request);
            case LIST_FILES:
                return handleListFilesRequest();
            case SEARCH_FILES:
                return handleSearchFilesRequest(request);
            case DELETE_FILE:
                return handleDeleteFileRequest(request);
            case LOGOUT:
                loggedInUsername = null;
                return new ServerResponse(true, "Logged out successfully.", null);
            default:
                return new ServerResponse(false, "Unknown or unsupported request type.", null);
        }
    }
    
    
    private void handleUploadFileRequest(ServerRequest request, ObjectInputStream ois, ObjectOutputStream oos) throws IOException {
        if (loggedInUsername == null || !(request.getData() instanceof FileMetadata)) {
            oos.writeObject(new ServerResponse(false, "Authentication required or invalid upload request.", null));
            oos.flush();
            return;
        }

        FileMetadata fileMetadata = (FileMetadata) request.getData();
        Path filePath = UPLOAD_DIR.resolve(fileMetadata.getFilename());

        if (dbHandler.getFileMetadataByName(fileMetadata.getFilename()) != null) {
            oos.writeObject(new ServerResponse(false, "File with this name already exists.", null));
            oos.flush();
            return;
        }

        try {
            
            oos.writeObject(new ServerResponse(true, "Ready to receive file.", null));
            oos.flush();

            
            receiveFile(ois, filePath, fileMetadata.getFileSize());

            
            FileMetadata completeMetadata = new FileMetadata(
                    fileMetadata.getFilename(),
                    fileMetadata.getFileSize(), 
                    loggedInUsername,
                    LocalDateTime.now());
            boolean metadataSaved = dbHandler.addFileMetadata(completeMetadata);

            
            ServerResponse finalResponse = new ServerResponse(metadataSaved,
                    metadataSaved ? "File uploaded successfully." : "File uploaded, but failed to save metadata.", null);
            oos.writeObject(finalResponse);
            oos.flush();

        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Error during file transfer: " + e.getMessage());
            Files.deleteIfExists(filePath); 
            
            if (clientSocket.isConnected() && !clientSocket.isClosed()) {
                oos.writeObject(new ServerResponse(false, "Error during file transfer on server: " + e.getMessage(), null));
                oos.flush();
            }
        }
    }

    
    private void receiveFile(ObjectInputStream ois, Path filePath, long expectedSize) throws IOException, ClassNotFoundException {
        long totalReceived = 0;
        try (OutputStream fos = Files.newOutputStream(filePath)) {
            while (totalReceived < expectedSize) {
                Object packetObj = ois.readObject();
                if (!(packetObj instanceof FileTransferPacket)) {
                    throw new IOException("Unexpected object received. Expected FileTransferPacket.");
                }
                FileTransferPacket packet = (FileTransferPacket) packetObj;
                byte[] data = packet.getData();
                if (data != null) {
                    fos.write(data);
                    totalReceived += data.length;
                }
            }
        }

        if (totalReceived != expectedSize) {
            Files.deleteIfExists(filePath);
            throw new IOException("File size mismatch. Expected: " + expectedSize + ", Received: " + totalReceived);
        }
        logger.log(Level.SEVERE, "Successfully received file: {0} ({1} bytes)", new Object[]{filePath.getFileName(), totalReceived});
    }
    
    
    private void handleDownloadFileRequest(ServerRequest request, ObjectOutputStream oos) throws IOException {
        if (loggedInUsername != null && request.getData() instanceof String) {
            String filenameToDownload = (String) request.getData();
            FileMetadata fileMetadata = dbHandler.getFileMetadataByName(filenameToDownload);
            Path filePath = UPLOAD_DIR.resolve(filenameToDownload);

            if (fileMetadata != null && Files.exists(filePath)) {
                
                oos.writeObject(new ServerResponse(true, "Server is ready to send file.", fileMetadata));
                oos.flush();
                
                sendFile(filePath, oos);
            } else {
                oos.writeObject(new ServerResponse(false, "File not found.", null));
                oos.flush();
            }
        } else {
            oos.writeObject(new ServerResponse(false, "Authentication required or invalid download request.", null));
            oos.flush();
        }
    }
    
    private void sendFile(Path filePath, ObjectOutputStream oos) throws IOException {
        long fileSize = Files.size(filePath);
        try (InputStream fis = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] dataToSend = java.util.Arrays.copyOf(buffer, bytesRead);
                FileTransferPacket packet = new FileTransferPacket(filePath.getFileName().toString(), dataToSend, 0, 0, fileSize, false); // isLast không quá quan trọng ở đây
                oos.writeObject(packet);
            }
            oos.flush();
        }
        logger.severe("Finished sending file: " + filePath.getFileName());
    }

    private ServerResponse handleAuthRequest(ServerRequest request) {
        if (!(request.getData() instanceof AuthRequest)) {
            return new ServerResponse(false, "Invalid auth request data.", null);
        }
        AuthRequest authData = (AuthRequest) request.getData();
        if (request.getType() == RequestType.LOGIN) {
            boolean success = dbHandler.verifyUser(authData.getUsername(), authData.getPassword());
            if (success) {
                loggedInUsername = authData.getUsername();
                return new ServerResponse(true, "Login successful!", loggedInUsername);
            }
            return new ServerResponse(false, "Invalid username or password.", null);
        } else { // REGISTER
            boolean success = dbHandler.addUser(authData.getUsername(), authData.getPassword());
            if (success) {
                return new ServerResponse(true, "Registration successful!", null);
            }
            return new ServerResponse(false, "Username might already exist.", null);
        }
    }

    private ServerResponse handleListFilesRequest() {
        if (loggedInUsername == null) return new ServerResponse(false, AUTHENTICATION_REQUIRED, null);
        List<FileMetadata> files = dbHandler.getAllFileMetadata(loggedInUsername); 
        return new ServerResponse(true, "File list retrieved.", new FileListResponse(files));
    }
    
    private ServerResponse handleSearchFilesRequest(ServerRequest request) {
        if (loggedInUsername == null) return new ServerResponse(false, AUTHENTICATION_REQUIRED, null);
        String query = (String) request.getData();
        List<FileMetadata> files = dbHandler.searchFileMetadata(query, loggedInUsername);
        return new ServerResponse(true, "Search results.", new FileListResponse(files));
    }

    private ServerResponse handleDeleteFileRequest(ServerRequest request) {
        if (loggedInUsername == null) return new ServerResponse(false, AUTHENTICATION_REQUIRED, null);
        String filename = (String) request.getData();
        FileMetadata metadata = dbHandler.getFileMetadataByName(filename);
        if (metadata == null) {
            return new ServerResponse(false, "File not found.", null);
        }
        if (!metadata.getUploader().equals(loggedInUsername)) {
            return new ServerResponse(false, "You can only delete your own files.", null);
        }
        try {
            Files.deleteIfExists(UPLOAD_DIR.resolve(filename));
            dbHandler.deleteFileMetadata(filename, loggedInUsername);
            return new ServerResponse(true, "File deleted successfully.", null);
        } catch (IOException e) {
            return new ServerResponse(false, "Error deleting file from disk.", null);
        }
    }
    
    private boolean requiresAuth(RequestType type) {
        return !(type == RequestType.LOGIN || type == RequestType.REGISTER);
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.severe("Error closing client socket: " + e.getMessage());
        }
    }
}