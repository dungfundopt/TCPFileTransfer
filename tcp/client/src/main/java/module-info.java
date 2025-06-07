module com.example.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires javafx.graphics; // Thường cần cho các ứng dụng JavaFX
    requires javafx.base;     // Thường cần cho các ứng dụng JavaFX
    
    requires shared;
    opens com.example.client to javafx.fxml;
    exports com.example.client;
}
