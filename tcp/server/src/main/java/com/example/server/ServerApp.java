package com.example.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerApp {
    private static final int PORT = 12345; // Cổng Server lắng nghe
    private static final int THREAD_POOL_SIZE = 10; // Số luồng tối đa xử lý client

    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
        // Khởi tạo DatabaseHandler một lần khi Server khởi động
        DatabaseHandler dbHandler = new DatabaseHandler();

        // Tạo Thread Pool để xử lý nhiều client cùng lúc
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            

            while (!serverSocket.isClosed()) {
                
                Socket clientSocket = serverSocket.accept(); // Chờ client kết nối
                

                // Giao phó việc xử lý client cho một luồng trong Thread Pool
                executor.execute(new ClientHandler(clientSocket, dbHandler));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown(); // Đóng Thread Pool khi Server dừng
        }
    }
}