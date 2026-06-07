package com.messenger.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

public class LoginController {

    @FXML private TextField ipField;
    @FXML private TextField portField; 
    @FXML private TextField usernameField;

    @FXML
    private void handleLoginButton(ActionEvent event) {
        if (ipField == null || portField == null || usernameField == null) {
            System.err.println("Ошибка: компоненты FXML не привязаны!");
            return;
        }
        
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty() || username.isEmpty()) {
            showAlert("Помилка", "Всі поля повинні бути заповнені.");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/messenger/client/view/chat-view.fxml"));
            Parent chatRoot = loader.load();

            ChatController chatController = loader.getController();
            chatController.initData(ip, port, username);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(chatRoot, 1200, 800));
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Помилка", "Не вдалося підключитися.");
        }
    }

    private void showAlert(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }
}