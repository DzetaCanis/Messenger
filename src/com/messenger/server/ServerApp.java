package com.messenger.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ServerApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Вказуємо шлях до вікна сервера
        Parent root = FXMLLoader.load(getClass().getResource("/com/messenger/server/view/server-view.fxml"));
        
        primaryStage.setTitle("ICQ Сервер");
        primaryStage.setScene(new Scene(root, 600, 600));
        primaryStage.setOnCloseRequest(event -> System.exit(0));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}