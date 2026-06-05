package com.messenger.server.controller;

import com.messenger.shared.model.Message;
import com.messenger.shared.model.XmlParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerController {

    @FXML private TextArea serverLogsArea;
    @FXML private ListView<String> activeSessionsList;

    private static final int PORT = 3128;
    private List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();

    @FXML
    public void initialize() {
        // === ІДЕАЛЬНА ВЕРСТКА СЕРВЕРА (50/50) ===
        Platform.runLater(() -> {
            if (serverLogsArea.getScene() != null) {
                AnchorPane root = (AnchorPane) serverLogsArea.getScene().getRoot();
                root.setStyle("-fx-background-color: linear-gradient(to bottom right, #e0f7fa, #ffffff);");
                
                Stage stage = (Stage) root.getScene().getWindow();
                if (stage != null) stage.setResizable(false); // Блокуємо зміну розміру вікна

                // Створюємо програмний контейнер, який ідеально поділить вікно навпіл
                VBox mainContainer = new VBox(15, serverLogsArea, activeSessionsList);
                VBox.setVgrow(serverLogsArea, Priority.ALWAYS); // 50% висоти
                VBox.setVgrow(activeSessionsList, Priority.ALWAYS); // 50% висоти

                root.getChildren().clear();
                root.getChildren().add(mainContainer);
                
                // Робимо рівні відступи від країв вікна
                AnchorPane.setTopAnchor(mainContainer, 15.0);
                AnchorPane.setBottomAnchor(mainContainer, 15.0);
                AnchorPane.setLeftAnchor(mainContainer, 15.0);
                AnchorPane.setRightAnchor(mainContainer, 15.0);
            }
        });

        serverLogsArea.setWrapText(true);
        serverLogsArea.setStyle("-fx-font-size: 16px; -fx-control-inner-background: white; -fx-border-color: #b0bec5; -fx-border-radius: 5;");
        activeSessionsList.setStyle("-fx-font-size: 16px; -fx-control-inner-background: white; -fx-border-color: #b0bec5; -fx-border-radius: 5;");

        activeSessionsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = activeSessionsList.getSelectionModel().getSelectedItem();
                if (selected != null && selected.startsWith("👤 ")) {
                    String target = selected.replace("👤 ", "").trim();
                    kickUser(target);
                }
            }
        });

        logEvent("Система: Ініціалізація серверного інтерфейсу...");
        startServer();
    }

    private void startServer() {
        Thread serverThread = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                Platform.runLater(() -> logEvent("Сервер запущено на порту " + PORT + ". Очікування..."));

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Platform.runLater(() -> logEvent("Нове підключення: " + clientSocket.getInetAddress().getHostAddress()));

                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    connectedClients.add(clientHandler);
                    
                    Thread clientThread = new Thread(clientHandler);
                    clientThread.setDaemon(true); 
                    clientThread.start();
                }
            } catch (Exception e) {
                Platform.runLater(() -> logEvent("Помилка сервера: " + e.getMessage()));
            }
        });
        serverThread.setDaemon(true); 
        serverThread.start();
    }

    public void logEvent(String eventInfo) {
        serverLogsArea.appendText(eventInfo + "\n");
    }

    private void kickUser(String targetName) {
        for (ClientHandler client : connectedClients) {
            if (client.clientName.equals(targetName)) {
                try {
                    Message kickMsg = new Message("SYSTEM", targetName, "KICK_USER", getCurrentTime(), "", "");
                    client.sendMessage(XmlParser.serialize(kickMsg));
                    Platform.runLater(() -> logEvent("Система: Користувача " + targetName + " кікнуто адміністратором."));
                    
                    new Thread(() -> {
                        try { Thread.sleep(500); client.socket.close(); } catch(Exception e){}
                    }).start();
                } catch (Exception e) {}
                break;
            }
        }
    }

    private void routeMessage(Message msg, String xmlMessage, ClientHandler sender) {
        String receiver = msg.getReceiver();
        if (receiver == null || receiver.equals("ALL")) {
            for (ClientHandler client : connectedClients) {
                if (client != sender) client.sendMessage(xmlMessage);
            }
        } else {
            for (ClientHandler client : connectedClients) {
                if (client.clientName.equals(receiver)) {
                    client.sendMessage(xmlMessage);
                    Platform.runLater(() -> logEvent("🔒 Приватно від " + sender.clientName + " до " + receiver));
                    break;
                }
            }
        }
    }

    private void updateActiveSessions() {
        List<String> activeNames = new ArrayList<>();
        for (ClientHandler client : connectedClients) {
            if (!client.clientName.equals("Невідомий")) activeNames.add(client.clientName);
        }

        Platform.runLater(() -> {
            activeSessionsList.getItems().clear();
            activeSessionsList.getItems().add("Всього онлайн: " + activeNames.size());
            for (String name : activeNames) activeSessionsList.getItems().add("👤 " + name);
        });

        try {
            Message contactsMsg = new Message("SYSTEM_CONTACT_LIST", "ALL", String.join(",", activeNames), getCurrentTime(), "", "");
            String xmlData = XmlParser.serialize(contactsMsg);
            for (ClientHandler client : connectedClients) client.sendMessage(xmlData);
        } catch (Exception e) {}
    }

    private String getCurrentTime() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private InputStream in;
        private OutputStream out;
        public String clientName = "Невідомий"; 

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = socket.getInputStream();
                this.out = socket.getOutputStream();
            } catch (Exception e) {}
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[64 * 1024];
                StringBuilder xmlBuilder = new StringBuilder(); 
                
                while (true) {
                    int readBytes = in.read(buffer);
                    if (readBytes == -1) break; 
                    
                    xmlBuilder.append(new String(buffer, 0, readBytes));
                    int endIdx;
                    while ((endIdx = xmlBuilder.indexOf("</message>")) != -1) {
                        String xmlMessage = xmlBuilder.substring(0, endIdx + 10);
                        xmlBuilder.delete(0, endIdx + 10);
                        Message msg = XmlParser.deserialize(xmlMessage);
                        
                        if (clientName.equals("Невідомий") && msg.getSender() != null) {
                            String proposedName = msg.getSender();
                            boolean nameExists = false;
                            
                            for (ClientHandler c : connectedClients) {
                                if (c != this && proposedName.equals(c.clientName)) {
                                    nameExists = true; break;
                                }
                            }
                            
                            if (nameExists) {
                                Message errorMsg = new Message("SYSTEM", proposedName, "NAME_TAKEN", getCurrentTime(), "", "");
                                sendMessage(XmlParser.serialize(errorMsg));
                                Platform.runLater(() -> logEvent("Відхилено: ім'я " + proposedName + " вже зайняте."));
                                Thread.sleep(500); 
                                return; 
                            }
                            
                            clientName = proposedName;
                            updateActiveSessions(); 
                        }

                        if (msg.getFileName() != null && !msg.getFileName().isEmpty()) {
                            Platform.runLater(() -> logEvent("📎 Користувач " + clientName + " відправив файл: " + msg.getFileName()));
                        } else {
                            Platform.runLater(() -> logEvent("Отримано повідомлення від " + clientName));
                        }
                        
                        routeMessage(msg, xmlMessage, this);
                    }
                }
            } catch (Exception e) {
            } finally {
                connectedClients.remove(this); 
                updateActiveSessions(); 
                if (!clientName.equals("Невідомий")) {
                    Platform.runLater(() -> logEvent("🔴 " + clientName + " відключився."));
                }
                try { socket.close(); } catch (Exception e) {}
            }
        }

        public void sendMessage(String msg) {
            try { out.write(msg.getBytes()); out.flush(); } catch (Exception e) {}
        }
    }
}