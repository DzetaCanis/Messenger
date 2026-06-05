package com.messenger.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField ipField;
    @FXML private TextField usernameField;

    @FXML
    public void initialize() {
        // === ГРАДІЄНТ НА ВІКНО ВХОДУ ===
        Platform.runLater(() -> {
            if (ipField.getScene() != null) {
                ipField.getScene().getRoot().setStyle("-fx-background-color: linear-gradient(to bottom right, #e0f7fa, #ffffff);");
            }
        });
    }

    @FXML
    private void handleLoginButton(ActionEvent event) {
        String ip = ipField.getText().trim();
        String username = usernameField.getText().trim();

        if (ip.isEmpty() || username.isEmpty()) {
            showAlert("Помилка вводу", "IP-адреса та ім'я користувача не можуть бути порожніми.");
            return;
        }

        // Обмеження на 20 символів
        if (username.length() > 20) {
            showAlert("Помилка вводу", "Ім'я користувача не може перевищувати 20 символів.");
            return;
        }

        String ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        if (!ip.equals("localhost") && !ip.matches(ipRegex)) {
            showAlert("Помилка формату", "Введіть коректну IP-адресу (наприклад, 127.0.0.1) або localhost.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/messenger/client/view/chat-view.fxml"));
            Parent chatRoot = loader.load();

            ChatController chatController = loader.getController();
            chatController.initData(ip, username);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            // Розмір вікна чату при відкритті
            stage.setScene(new Scene(chatRoot, 1200, 800));
            stage.setTitle("ICQ Клієнт - Чат (" + username + ") | Символів: 0/1000"); 
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Системна помилка", "Не вдалося завантажити інтерфейс чату.");
        }
    }

    @FXML
    private void handleCancelButton(ActionEvent event) {
        System.exit(0);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}