package com.example.client;

// Import các lớp từ shared module
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;

import com.example.shared.FileListResponse;
import com.example.shared.FileMetadata;
import com.example.shared.ServerResponse;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task; // !!! Cần import Task !!!
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ClientController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private TextField searchField;
    @FXML
    private Button downloadButton;
    @FXML
    private Button uploadButton;
    @FXML
    private TableView<FileMetadata> fileTableView;
    @FXML
    private ProgressBar progressBar;

    @FXML
    private TextField downloadFilenameField;

    @SuppressWarnings("unused")
    private String loggedInUsername;
    private File selectedFileForUpload;

    private final ObservableList<FileMetadata> masterFileList = FXCollections.observableArrayList();
    private FilteredList<FileMetadata> filteredFileList;
    private String uploadfailed = "Upload Failed";
    // !!! Thêm trường ServerClient !!!
    private ServerClient serverClient;

     // Phương thức để nhận ServerClient instance từ LoginController
    public void setServerClient(ServerClient serverClient) {
        this.serverClient = serverClient;
        
        // Sau khi nhận ServerClient, có thể tải danh sách file ngay
        if (this.serverClient != null && this.serverClient.isConnected()) {
             loadFileList();
        } else {
             App.showAlert("Connection Error", "Failed to connect to server after login.");
             // Quay về màn hình Login nếu không kết nối được
             Platform.runLater(() -> {
                 try {
                     // Lấy Stage từ một control bất kỳ trong scene hiện tại
                     Stage stage = (Stage) welcomeLabel.getScene().getWindow();
                     FXMLLoader loader = new FXMLLoader(getClass().getResource("Login.fxml"));
                     Parent root = loader.load();
                     Scene scene = new Scene(root);
                     stage.setScene(scene);
                     stage.setMaximized(true);
                     stage.show();
                 } catch (IOException e) {
                     
                     App.showAlert("Error", "Could not return to login page.");
                 }
             });
        }
    }


    @SuppressWarnings("unchecked")
    @FXML
    public void initialize() {
        // Cấu hình các cột của TableView (giữ nguyên)
        TableColumn<FileMetadata, String> filenameColumn = (TableColumn<FileMetadata, String>) fileTableView.getColumns().get(0);
        filenameColumn.setCellValueFactory(new PropertyValueFactory<>("filename"));

        TableColumn<FileMetadata, String> sizeColumn = (TableColumn<FileMetadata, String>) fileTableView.getColumns().get(1);
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("formattedFileSize"));

        TableColumn<FileMetadata, String> uploaderColumn = (TableColumn<FileMetadata, String>) fileTableView.getColumns().get(2);
        uploaderColumn.setCellValueFactory(new PropertyValueFactory<>("uploader"));

        TableColumn<FileMetadata, String> uploadTimeColumn = (TableColumn<FileMetadata, String>) fileTableView.getColumns().get(3);
        uploadTimeColumn.setCellValueFactory(new PropertyValueFactory<>("formattedUploadTime"));


        filteredFileList = new FilteredList<>(masterFileList, p -> true);
        SortedList<FileMetadata> sortedFileList = new SortedList<>(filteredFileList);
        sortedFileList.comparatorProperty().bind(fileTableView.comparatorProperty());
        fileTableView.setItems(sortedFileList);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterFileList(newValue));

        fileTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                downloadFilenameField.setText(newSelection.getFilename());
                downloadButton.setDisable(false);
            } else {
                downloadFilenameField.setText("");
                 downloadButton.setDisable(true);
            }
        });

        downloadButton.setDisable(true);
        uploadButton.setDisable(true);
        progressBar.setVisible(false);
        // progressBar.progressProperty().unbind(); // Không cần unbind ở đây
        progressBar.setProgress(0); // Reset progress bar ban đầu

        // loadFileList(); // loadFileList sẽ được gọi sau khi setServerClient
    }

    public void setUsername(String username) {
        this.loggedInUsername = username;
        welcomeLabel.setText("TCP File transfer - Welcome, " + username + "!");
    }

    @FXML
    void handleLogout(ActionEvent event) {
        
         if (serverClient != null) {
              ServerResponse logoutResponse = serverClient.logout(); // Gửi yêu cầu logout
              if (logoutResponse != null) {
                  // No additional action needed on successful logout response
              }
             serverClient = null; // Xóa instance
         }

        try {
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            
            App.showAlert("Error", "Could not load login page.");
        }
    }

     @FXML
     void handleRefresh(ActionEvent event) {
          
          loadFileList();
          searchField.clear();
          downloadFilenameField.clear();
     }

    @FXML
    void handleSelectFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        // Lấy Stage hiện tại từ một control để hiển thị FileChooser
        Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        selectedFileForUpload = fileChooser.showOpenDialog(currentStage);

        if (selectedFileForUpload != null) {
            
            uploadButton.setDisable(false);
        } else {
            
            selectedFileForUpload = null;
            uploadButton.setDisable(true);
        }
    }


    @FXML
    @SuppressWarnings("unused")
    void handleUpload(ActionEvent event) {
         if (selectedFileForUpload == null || !selectedFileForUpload.exists()) {
             App.showAlert("Upload Error", "No file selected or selected file does not exist.");
             uploadButton.setDisable(true);
             return;
         }

         if (serverClient == null || !serverClient.isConnected()) {
             App.showAlert("Upload Error", "Not connected to server.");
             return;
         }

         String filename = selectedFileForUpload.getName();
         long fileSize = selectedFileForUpload.length();

          boolean fileExistsLocally = masterFileList.stream()
                                      .anyMatch(f -> f.getFilename().equals(filename));
         if (fileExistsLocally) {
             
             App.showAlert(uploadfailed, "A file with this name already exists on the server.");
             return;
         }

        

        // --- Thực hiện Upload trong luồng nền ---
        Task<ServerResponse> uploadTask = new Task<>() {
            @Override
            protected ServerResponse call() throws Exception {
                 // Truyền callback BiConsumer vào ServerClient.uploadFile
                 BiConsumer<Long, Long> progressCallback = (sentBytes, totalSize) -> 
                     updateProgress(sentBytes, totalSize);

                 // Gọi phương thức uploadFile từ ServerClient
                 return serverClient.uploadFile(selectedFileForUpload, progressCallback); // Trả về phản hồi cuối cùng từ Server
            }

            @Override
            protected void succeeded() {
                 ServerResponse response = getValue();
                 
                 Platform.runLater(() -> {
                     if (response != null && response.isSuccess()) {
                          App.showAlert("Upload Success", response.getMessage());
                          loadFileList(); // Tải lại danh sách để thấy file mới
                     } else {
                          App.showAlert(uploadfailed, "Server reported failure: " + (response != null ? response.getMessage() : "Unknown error."));
                     }
                     progressBar.setVisible(false);
                     uploadButton.setDisable(true);
                     selectedFileForUpload = null;
                     progressBar.progressProperty().unbind(); // Bỏ bind
                     progressBar.setProgress(0);
                 });
            }

            @Override
            protected void failed() {
                Throwable exception = getException();
                
                Platform.runLater(() -> {
                    App.showAlert(uploadfailed, "Error during upload: " + exception.getMessage());
                    progressBar.setVisible(false);
                    uploadButton.setDisable(true);
                     selectedFileForUpload = null;
                     progressBar.progressProperty().unbind(); // Bỏ bind
                     progressBar.setProgress(0);
                });
            }

             @Override
             protected void cancelled() {
                 
                 Platform.runLater(() -> {
                      App.showAlert("Upload Cancelled", "File upload was cancelled.");
                      progressBar.setVisible(false);
                      uploadButton.setDisable(true);
                      selectedFileForUpload = null;
                      progressBar.progressProperty().unbind(); // Bỏ bind
                      progressBar.setProgress(0);
                 });
             }
        }; // <-- Dấu chấm phẩy ở cuối Task


        // Bind progress bar với task's progress property
        progressBar.progressProperty().bind(uploadTask.progressProperty());
        progressBar.setVisible(true);

        uploadButton.setDisable(true);

        // Chạy task trong một luồng mới
        new Thread(uploadTask).start();
    }


    @FXML
    @SuppressWarnings("unused")
    void handleDownload(ActionEvent event) {
         String filenameToDownload = downloadFilenameField.getText();

         if (filenameToDownload == null || filenameToDownload.trim().isEmpty()) {
             App.showAlert("Download Error", "Please select a file from the list or enter a filename.");
             return;
         }

          if (serverClient == null || !serverClient.isConnected()) {
             App.showAlert("Download Error", "Not connected to server.");
             return;
         }

         // Mở FileChooser để người dùng chọn vị trí lưu file
         FileChooser fileChooser = new FileChooser();
         fileChooser.setTitle("Save File");
         fileChooser.setInitialFileName(filenameToDownload);
         Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
         File saveFile = fileChooser.showSaveDialog(currentStage);

         if (saveFile == null) {
             
             return;
         }

         

         // --- Thực hiện Download trong luồng nền ---
         Task<FileMetadata> downloadTask = new Task<>() { // Task trả về FileMetadata sau khi tải xong
              @Override
              protected FileMetadata call() throws Exception {
                   if (serverClient == null || !serverClient.isConnected()) {
                       throw new IllegalStateException("Not connected or not logged in.");
                   }
                   if (filenameToDownload == null || filenameToDownload.trim().isEmpty()) {
                       throw new IllegalArgumentException("Filename cannot be empty for download.");
                   }
                   if (saveFile == null) {
                        throw new IllegalArgumentException("Save location cannot be null.");
                   }

                   // Truyền callback BiConsumer vào ServerClient.downloadFile
                   BiConsumer<Long, Long> progressCallback = (receivedBytes, totalSize) ->
                       updateProgress(receivedBytes, totalSize); // Cập nhật Task's progress

                   // Gọi phương thức downloadFile từ ServerClient
                   return serverClient.downloadFile(filenameToDownload, saveFile, progressCallback); // Task trả về metadata của file đã tải
               } // Kết thúc call()

            @Override
            protected void succeeded() {
                handleDownloadSuccess(getValue());
            }

           @Override
           protected void failed() {
                handleDownloadFailed(getException());
            }

            @Override
            protected void cancelled() {
                handleDownloadCancelled();
            }
        }; // <-- Dấu chấm phẩy ở cuối Task


           // Bind progress bar với task's progress property
           progressBar.progressProperty().bind(downloadTask.progressProperty());
           progressBar.setVisible(true);

           // Chạy task trong một luồng mới
           new Thread(downloadTask).start();
       }


        private void loadFileList() {
             if (serverClient == null || !serverClient.isConnected()) {
                 
                 Platform.runLater(() -> {
                     masterFileList.clear();
                     filteredFileList.setPredicate(p -> false); // Hiển thị trống
                      App.showAlert("Connection Error", "Not connected to server. Cannot load file list.");
                 });
                 return;
             }
             

             // --- Thực hiện Load File List trong luồng nền ---
             Task<List<FileMetadata>> loadTask = new Task<>() {
                 @Override
                 protected List<FileMetadata> call() throws Exception {
                      if (serverClient.getFileList() != null && serverClient.getFileList().isSuccess() && serverClient.getFileList().getData() instanceof FileListResponse) {
                           return ((FileListResponse) serverClient.getFileList().getData()).getFileList();
                      } else {
                           throw new Exception("Failed to load file list: " + ((serverClient.getFileList() != null) ? serverClient.getFileList().getMessage() : "Unknown server response."));
                      }
                 }

                 @Override
                 protected void succeeded() {
                      Platform.runLater(() -> {
                          masterFileList.setAll(getValue());
                          filterFileList(searchField.getText());
                      });
                 }

                 @Override
                 protected void failed() {
                       Platform.runLater(() -> {
                           masterFileList.clear();
                           filteredFileList.setPredicate(p -> false);
                           App.showAlert("Error Loading Files", "Could not load file list: " + getException().getMessage());
                       });
                 }
             }; // <-- Dấu chấm phẩy ở cuối Task


             new Thread(loadTask).start();
        }


        private void filterFileList(String searchText) {
             if (searchText == null || searchText.isEmpty()) {
                 filteredFileList.setPredicate(p -> true);
             } else {
                 String lowerCaseFilter = searchText.toLowerCase();
                 filteredFileList.setPredicate(file -> {
                     if (file.getFilename().toLowerCase().contains(lowerCaseFilter)) {
                         return true;
                     }
                     return file.getUploader() != null && file.getUploader().toLowerCase().contains(lowerCaseFilter);
                 });
             }
        }
        private void handleDownloadSuccess(FileMetadata downloadedMetadata) {
            Platform.runLater(() -> {
                if (downloadedMetadata != null) {
                    App.showAlert("Download Success", "File '" + downloadedMetadata.getFilename() + "' downloaded successfully!");
                } else {
                    App.showAlert("Download Success", "File downloaded successfully (metadata not returned).");
                }
                resetProgressBar();
            });
        }
        private void handleDownloadFailed(Throwable exception) {
            Platform.runLater(() -> {
                App.showAlert("Download Failed", "Error during download: " + exception.getMessage());
                resetProgressBar();
            });
        }
        private void handleDownloadCancelled() {
            Platform.runLater(() -> {
                App.showAlert("Download Cancelled", "File download was cancelled.");
                resetProgressBar();
            });
        }
        private void resetProgressBar() {
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
        }
    }