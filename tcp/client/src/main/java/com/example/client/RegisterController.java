package com.example.client;

import java.io.IOException;

import com.example.shared.ServerResponse;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label errorLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField usernameField;

    

    @FXML
    public void initialize() {
        errorLabel.setText(""); // Xóa text lỗi ban đầu
    }

    @FXML
    @SuppressWarnings({ "unused", "resource" })
    void handleRegister(ActionEvent event) {
        ServerClient serverClient;
        errorLabel.setText(""); // Xóa thông báo lỗi cũ

        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Password and Confirm Password do not match.");
            passwordField.clear();
            confirmPasswordField.clear();
            return;
        }

        serverClient = new ServerClient();
        ServerResponse registerResponse = serverClient.register(username, password);

        if (registerResponse != null && registerResponse.isSuccess()) {
            errorLabel.setText("Registration successful! You can now log in.");
            usernameField.clear();
            passwordField.clear();
            confirmPasswordField.clear();
        } else {
            String errorMessage = (registerResponse != null) ? registerResponse.getMessage() : "Server communication error.";
            errorLabel.setText("Registration failed: " + errorMessage);
        }
    }

    @FXML
    @SuppressWarnings("unused")
    void handleBackToLogin(ActionEvent event) {
        
        try {
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            
            App.showAlert("Error", "Could not load login page.");
        }
    }
}