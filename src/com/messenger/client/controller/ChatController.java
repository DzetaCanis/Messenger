package com.messenger.client.controller;

import com.messenger.shared.model.Message;
import com.messenger.shared.model.XmlParser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {

    private String selectedReceiver = "ALL"; 
    private Map<String, List<Node>> chatHistories = new HashMap<>();

    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatBox;
    @FXML private ListView<String> contactsList;
    @FXML private TextArea messageInput;

    private String serverIp;
    private String username;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        // Налаштування кастомного вирівнювання для списку (Профіль по центру, інше - зліва)
        contactsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    if (item.contains("МЙ ПРОФІЛЬ") || item.contains("Ім'я:") || item.contains("Хост:")) {
                        setStyle("-fx-alignment: center; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #444444;");
                    } else {
                        setStyle("-fx-alignment: center-left; -fx-font-size: 16px;");
                    }
                }
            }
        });

        // М'який блакитний діагональний градієнт
        chatScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: lightgray;");
        chatBox.setStyle("-fx-background-color: linear-gradient(to bottom right, #e0f7fa, #ffffff);");
        
        // Логіка перемикання між чатами
        contactsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
            
            if (newValue.startsWith("👤 ")) {
                selectedReceiver = newValue.replace("👤 ", "").replace(" (Ви - Збережені)", "").trim();
                messageInput.setPromptText("Приватне повідомлення для " + selectedReceiver + "...");
            } else {
                // Якщо клікнули на Профіль або "В мережі" — повертаємось у загальний чат
                selectedReceiver = "ALL";
                messageInput.setPromptText("Написати всім...");
            }
            refreshChatBox();
        });
        
        chatBox.setPadding(new Insets(15));
        chatBox.setPrefHeight(VBox.USE_COMPUTED_SIZE);
        chatBox.setSpacing(15);
        
        // Щоб градієнт тягнувся на весь екран навіть якщо повідомлень мало
        chatScrollPane.setFitToHeight(true); 
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setFitToWidth(true); 
        
        chatBox.heightProperty().addListener((o, oldV, newV) -> Platform.runLater(() -> chatScrollPane.setVvalue(1.0)));

        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume(); 
                handleSendMessage(null); 
            }
        });
    }

    public void initData(String ip, String username) {
        this.serverIp = ip;
        this.username = username;
        connectToServer();
    }
    
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(serverIp, 3128);
                out = socket.getOutputStream();
                in = socket.getInputStream();
                
                appendMessage("ALL", getCurrentTime(), "Система", "Успішно підключено!", false, true, null, null);
                
                Message loginMsg = new Message(username, "ALL", "приєднався до чату", getCurrentTime(), "", "");
                out.write(XmlParser.serialize(loginMsg).getBytes());
                out.flush();

                byte[] buffer = new byte[64 * 1024];
                StringBuilder xmlBuilder = new StringBuilder();

                while (true) {
                    int readBytes = in.read(buffer);
                    if (readBytes == -1) break;
                    xmlBuilder.append(new String(buffer, 0, readBytes));
                    
                    int endIdx;
                    while ((endIdx = xmlBuilder.indexOf("</message>")) != -1) {
                        String xmlData = xmlBuilder.substring(0, endIdx + 10);
                        xmlBuilder.delete(0, endIdx + 10);
                        Message receivedMsg = XmlParser.deserialize(xmlData);
                        
                        // ОБРОБКА КІКУ З СЕРВЕРА
                        if ("SYSTEM".equals(receivedMsg.getSender()) && "KICK_USER".equals(receivedMsg.getText())) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Відключення");
                                alert.setHeaderText(null);
                                alert.setContentText("Вас було видалено з сервера адміністратором.");
                                alert.showAndWait();
                                System.exit(0);
                            });
                            return;
                        }

                        if ("SYSTEM_CONTACT_LIST".equals(receivedMsg.getSender())) {
                            String[] onlineUsers = receivedMsg.getText().split(",");
                            Platform.runLater(() -> {
                                contactsList.getItems().clear(); 
                                // МІЙ ПРОФІЛЬ (Без рамки, вирівнювання налаштовується в CellFactory)
                                contactsList.getItems().add("МЙ ПРОФІЛЬ");
                                contactsList.getItems().add("Ім'я: " + username);
                                contactsList.getItems().add("Хост: " + serverIp);
                                contactsList.getItems().add(""); // Порожній рядок для відступу
                                
                                contactsList.getItems().add("👥 В мережі (" + onlineUsers.length + "):");
                                for (String user : onlineUsers) {
                                    if (!user.trim().isEmpty()) {
                                        contactsList.getItems().add("👤 " + user + (user.equals(username) ? " (Ви - Збережені)" : ""));
                                    }
                                }
                            });
                        } else {
                            if (!receivedMsg.getSender().equals(username)) {
                                String chatTab = receivedMsg.getReceiver().equals("ALL") ? "ALL" : receivedMsg.getSender();
                                appendMessage(chatTab, receivedMsg.getTime(), receivedMsg.getSender(), receivedMsg.getText(), false, false, receivedMsg.getFileName(), receivedMsg.getFileData());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                appendMessage("ALL", getCurrentTime(), "Система", "Втрачено з'єднання.", false, true, null, null);
            }
        }).start();
    }

    private void refreshChatBox() {
        chatBox.getChildren().clear();
        if (chatHistories.containsKey(selectedReceiver)) {
            chatBox.getChildren().addAll(chatHistories.get(selectedReceiver));
        }
    }

    @FXML
    private void handleSendMessage(ActionEvent event) {
        String text = messageInput.getText().trim();
        if (!text.isEmpty() && socket != null && !socket.isClosed()) {
            try {
                String currentTime = getCurrentTime();
                Message msg = new Message(username, selectedReceiver, text, currentTime, "", "");
                out.write(XmlParser.serialize(msg).getBytes());
                out.flush();
                
                appendMessage(selectedReceiver, currentTime, "Ви", text, true, false, null, null);
                messageInput.clear();
            } catch (Exception e) {
                appendMessage(selectedReceiver, getCurrentTime(), "Система", "Помилка відправки!", false, true, null, null);
            }
        }
    }

    @FXML
    private void handleSendFile(ActionEvent event) {
        if (socket == null || socket.isClosed()) return;
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(chatBox.getScene().getWindow());

        if (file != null) {
            String targetChat = selectedReceiver; 
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                    String currentTime = getCurrentTime();
                    Message msg = new Message(username, targetChat, "Надіслано файл", currentTime, file.getName(), base64Data);
                    out.write(XmlParser.serialize(msg).getBytes());
                    out.flush();

                    appendMessage(targetChat, currentTime, "Ви", "Файл", true, false, file.getName(), base64Data);
                } catch (Exception ex) {
                    appendMessage(targetChat, getCurrentTime(), "Система", "Помилка відправки файлу.", false, true, null, null);
                }
            }).start();
        }
    }

    // === ЛЕГКИЙ ДИЗАЙН ПОВІДОМЛЕНЬ (БЕЗ РАМОК, ВИТРИМАНИЙ СТИЛЬ) ===
    private void appendMessage(String chatIdentifier, String time, String senderName, String text, boolean isOwn, boolean isSystem, String fileName, String base64File) {
        Platform.runLater(() -> {
            VBox messageContainer = new VBox(2);
            messageContainer.setMaxWidth(500); 

            Text timeNode = new Text("[" + time + "] ");
            timeNode.setFill(Color.GRAY);
            timeNode.setStyle("-fx-font-size: 13px;");

            Text senderNode = new Text(senderName + "\n");
            // Різні кольори для імені: свої - сині, чужі - темно-зелені
            senderNode.setStyle("-fx-font-weight: bold; -fx-fill: " + (isOwn ? "#005A9E" : "#0A7E07") + "; -fx-font-size: 16px;");

            Node contentNode;
            if (fileName != null && !fileName.isEmpty()) {
                Hyperlink fileLink = new Hyperlink("📎 Завантажити: " + fileName);
                fileLink.setStyle("-fx-text-fill: #0000EE; -fx-underline: true; -fx-font-size: 16px;");
                fileLink.setOnAction(e -> saveFile(fileName, base64File));
                contentNode = fileLink;
            } else {
                Text textNode = new Text(text);
                textNode.setStyle("-fx-font-size: 16px; -fx-fill: #333333;");
                contentNode = textNode;
            }

            TextFlow flow = new TextFlow(timeNode, senderNode, contentNode);
            messageContainer.getChildren().add(flow);

            HBox row = new HBox();
            
            // Розміщення праворуч або ліворуч
            if (isSystem) {
                row.setAlignment(Pos.CENTER);
                senderNode.setStyle("-fx-font-weight: bold; -fx-fill: gray; -fx-font-size: 16px;");
            } else if (isOwn) {
                row.setAlignment(Pos.CENTER_RIGHT); // Свої праворуч
            } else {
                row.setAlignment(Pos.CENTER_LEFT); // Чужі ліворуч
            }

            row.getChildren().add(messageContainer);

            chatHistories.putIfAbsent(chatIdentifier, new ArrayList<>());
            chatHistories.get(chatIdentifier).add(row);

            if (chatIdentifier.equals(selectedReceiver)) {
                chatBox.getChildren().add(row);
            }
        });
    }

    private void saveFile(String fileName, String base64Data) {
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Зберегти файл");
        saveChooser.setInitialFileName(fileName);
        File saveFile = saveChooser.showSaveDialog(chatBox.getScene().getWindow());
        if (saveFile != null) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
                Files.write(saveFile.toPath(), decodedBytes);
                appendMessage(selectedReceiver, getCurrentTime(), "Система", "Файл успішно збережено!", false, true, null, null);
            } catch (Exception ex) {}
        }
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(timeFormatter);
    }
}