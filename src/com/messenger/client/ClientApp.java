package com.messenger.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

	@Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/messenger/client/view/login-view.fxml"));
        
        primaryStage.setTitle("ICQ Клієнт - Вхід");
        primaryStage.setScene(new Scene(root)); 
        primaryStage.setResizable(false); 
        
        // Він жорстко завершує роботу програми і розриває всі сокети при закритті вікна
        primaryStage.setOnCloseRequest(event -> System.exit(0));
        
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}