package com.example.client;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent; // Cần import Alert
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image; // Cần import Platform
import javafx.stage.Stage; // Cần import Image

public class App extends Application {

    private static Stage primaryStage; // Lưu Stage chính

    @Override
    public void start(@SuppressWarnings("exports") Stage stage) throws IOException {
    // Load icon
    try {
        Image icon = new Image(getClass().getResourceAsStream("/icon.png")); // Sửa lỗi chính tả getResourceAsStream
        stage.getIcons().add(icon); // Sửa lỗi chính tả getIcons
    } catch (Exception e) {
        
        // Ứng dụng vẫn chạy nếu không load được icon
    }

    Scene scene = new Scene(loadFXML("Login"), 640, 480); // Sửa lỗi cú pháp tham số
    stage.setScene(scene); // Sửa biến scene viết hoa
    stage.setTitle("TCP File Transfer"); // Tiêu đề ứng dụng

    stage.setWidth(1550); // Sửa primaryStage thành stage
    stage.setHeight(800); // Sửa primaryStage thành stage

    stage.show();
}


    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

    /**
     * Phương thức tĩnh để hiển thị Alert từ bất kỳ đâu trong ứng dụng.
     * @param title Tiêu đề Alert.
     * @param message Nội dung Alert.
     */
    public static void showAlert(String title, String message) {
        // Đảm bảo chạy trên UI Thread
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION); // Hoặc Alert.AlertType.ERROR
            alert.setTitle(title);
            alert.setHeaderText(null); // Không có HeaderText
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

     // Tùy chọn: Getter cho primary Stage nếu cần
     @SuppressWarnings("exports")
    public static Stage getPrimaryStage() {
         return primaryStage;
     }
}