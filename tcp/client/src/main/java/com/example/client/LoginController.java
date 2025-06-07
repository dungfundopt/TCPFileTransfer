package com.example.client;

import java.io.IOException;

import com.example.shared.ServerResponse;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField usernameField;

    private ServerClient serverClient;

    @FXML
    public void initialize() {
        // Initialization code if needed
    }

    @FXML
    void handleLogin(ActionEvent event) {
        performLogin();
    }

    @FXML
    void handleRegister(ActionEvent event) {
        try {
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Register.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            App.showAlert("Error", "Could not load registration page: " + e.getMessage());
        }
    }

    private void performLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            App.showAlert("Login Failed", "Username and password cannot be empty.");
            return;
        }

        try {
            serverClient = new ServerClient();
            ServerResponse loginResponse = serverClient.authenticate(username, password);

            if (loginResponse != null && loginResponse.isSuccess()) {
                String loggedInUsername = (String) loginResponse.getData();
                App.showAlert("Login Successful", loginResponse.getMessage());

                Stage stage = (Stage)usernameField.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("Client.fxml"));
                Parent mainAppRoot = loader.load();

                ClientController clientController = loader.getController();
                clientController.setServerClient(serverClient);
                clientController.setUsername(loggedInUsername);

                Scene mainAppScene = new Scene(mainAppRoot);
                stage.setScene(mainAppScene);
                stage.show();

            } else {
                String errorMessage = (loginResponse != null) ? loginResponse.getMessage() : "Server communication error.";
                App.showAlert("Login Failed", errorMessage);
                closeServerConnection();
            }
        } catch (IOException e) {
            App.showAlert("Error", "Could not connect to server or load main application: " + e.getMessage());
            closeServerConnection();
        }
    }

    private void closeServerConnection() {
        if (serverClient != null) {
            serverClient.closeConnection();
        }
    }

    public ServerClient getServerClient() {
        return serverClient;
    }
}