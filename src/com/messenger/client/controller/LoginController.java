package com.messenger.client.controller;

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

    @FXML
    private TextField ipField;

    @FXML
    private TextField usernameField;

    @FXML
    private void handleLoginButton(ActionEvent event) {
        // Очищаємо текст від зайвих пробілів по краях
        String ip = ipField.getText().trim();
        String username = usernameField.getText().trim();

        // 1. Перевірка: чи не порожні поля?
        if (ip.isEmpty() || username.isEmpty()) {
            showAlert("Помилка вводу", "IP-адреса та ім'я користувача не можуть бути порожніми.");
            return; // Зупиняємо виконання методу
        }

        // 2. Перевірка формату IP-адреси (дозволяємо localhost або стандартний формат 0.0.0.0)
        // Використовуємо регулярний вираз для перевірки
        String ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        if (!ip.equals("localhost") && !ip.matches(ipRegex)) {
            showAlert("Помилка формату", "Введіть коректну IP-адресу (наприклад, 127.0.0.1) або localhost.");
            return;
        }

        // 3. Якщо все добре — переходимо до вікна чату
        try {
            // Завантажуємо файл розмітки чату
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/messenger/client/view/chat-view.fxml"));
            Parent chatRoot = loader.load();

            // Отримуємо доступ до контролера чату, щоб передати йому введені дані
            ChatController chatController = loader.getController();
            chatController.initData(ip, username);

            // Отримуємо поточне вікно (Stage), з якого була натиснута кнопка
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Змінюємо сцену на чат (розмір 600x400)
            stage.setScene(new Scene(chatRoot, 1000, 800));
            stage.setTitle("ICQ Клієнт - Чат (" + username + ")"); // Додаємо ім'я в заголовок вікна
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Системна помилка", "Не вдалося завантажити інтерфейс чату.");
        }
    }

    @FXML
    private void handleCancelButton(ActionEvent event) {
        // Закриваємо програму
        System.exit(0);
    }

    // Допоміжний метод для зручного створення вікон з помилками
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}