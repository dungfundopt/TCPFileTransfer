package com.example.client;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent; 
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image; 
import javafx.stage.Stage; 

public class App extends Application {

    private static Stage primaryStage; 

    @Override
    public void start(@SuppressWarnings("exports") Stage stage) throws IOException {
    // Load icon
    try {
        Image icon = new Image(getClass().getResourceAsStream("/com/example/client/images/icon.png")); // Sửa lỗi chính tả getResourceAsStream
        stage.getIcons().add(icon); 
    } catch (Exception e) {
        
        
    }

    Scene scene = new Scene(loadFXML("Login"), 640, 480); 
    stage.setScene(scene); 
    stage.setTitle("TCP File Transfer"); 

    stage.setWidth(1550); 
    stage.setHeight(800); 
    stage.show();
}


    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
    public static void showAlert(String title, String message) {
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION); 
            alert.setTitle(title);
            alert.setHeaderText(null); 
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

     @SuppressWarnings("exports")
    public static Stage getPrimaryStage() {
         return primaryStage;
     }
}