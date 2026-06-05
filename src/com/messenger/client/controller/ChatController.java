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
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
    @FXML private Node chatBox; // Більше не використовуємо його напряму, щоб уникнути помилок FXML
    @FXML private ListView<String> contactsList;
    @FXML private TextArea messageInput;

    // Власний безпечний контейнер для чату
    private VBox dynamicChatBox;

    private String serverIp;
    private String username;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            if (chatScrollPane.getScene() != null) {
                chatScrollPane.getScene().getRoot().setStyle("-fx-background-color: linear-gradient(to bottom right, #e0f7fa, #ffffff);");
            }
        });
        
        // === СТВОРЮЄМО НЕЗАЛЕЖНИЙ КОНТЕЙНЕР ДЛЯ ЧАТУ ===
        // Це виправляє проблему накладання тексту та непрацюючі вкладки!
        dynamicChatBox = new VBox(15);
        dynamicChatBox.setPadding(new Insets(15));
        dynamicChatBox.setStyle("-fx-background-color: white;");
        chatScrollPane.setContent(dynamicChatBox);
        chatScrollPane.setFitToWidth(true);
        
        dynamicChatBox.heightProperty().addListener((o, oldV, newV) -> Platform.runLater(() -> chatScrollPane.setVvalue(1.0)));

        chatScrollPane.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: #b0bec5; -fx-border-radius: 5; -fx-background-radius: 5;");
        contactsList.setStyle("-fx-background-color: white; -fx-border-color: #b0bec5; -fx-border-radius: 5; -fx-background-radius: 5;");
        messageInput.setStyle("-fx-border-color: #b0bec5; -fx-border-radius: 5; -fx-background-radius: 5;");

        // Жорстко фіксуємо ширину поля вводу по ширині вікна чату
        messageInput.prefWidthProperty().bind(chatScrollPane.widthProperty());

        contactsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    setMouseTransparent(true);
                } else {
                    setText(item);
                    if (item.contains("МЙ ПРОФІЛЬ") || item.contains("Ім'я:") || item.contains("Хост:") || item.trim().isEmpty()) {
                        setStyle("-fx-background-color: transparent; -fx-alignment: center; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #555555;");
                        setMouseTransparent(true); 
                    } else {
                        setStyle("-fx-alignment: center-left; -fx-font-size: 16px;");
                        setMouseTransparent(false); 
                    }
                }
            }
        });

        contactsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
            if (newValue.startsWith("👤 ")) {
                selectedReceiver = newValue.replace("👤 ", "").replace(" (Ви - Збережені)", "").trim();
                messageInput.setPromptText("Приватне повідомлення для " + selectedReceiver + "...");
            } else if (newValue.contains("В мережі")) {
                selectedReceiver = "ALL";
                messageInput.setPromptText("Написати всім...");
            }
            refreshChatBox();
        });

        messageInput.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 1000) {
                messageInput.setText(oldValue); 
            } else {
                Platform.runLater(() -> {
                    if (messageInput.getScene() != null && messageInput.getScene().getWindow() != null) {
                        Stage stage = (Stage) messageInput.getScene().getWindow();
                        stage.setTitle("ICQ Клієнт - Чат (" + username + ") | Символів: " + messageInput.getText().length() + "/1000");
                    }
                });
            }
        });

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
                        
                        if ("SYSTEM".equals(receivedMsg.getSender())) {
                            if ("KICK_USER".equals(receivedMsg.getText())) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Відключення");
                                    alert.setHeaderText(null);
                                    alert.setContentText("Вас було видалено з сервера адміністратором.");
                                    alert.showAndWait();
                                    System.exit(0);
                                });
                                return;
                            } else if ("NAME_TAKEN".equals(receivedMsg.getText())) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Помилка входу");
                                    alert.setHeaderText(null);
                                    alert.setContentText("Це ім'я вже зайняте! Будь ласка, оберіть інше.");
                                    alert.showAndWait();
                                    System.exit(0);
                                });
                                return;
                            }
                        }

                        if ("SYSTEM_CONTACT_LIST".equals(receivedMsg.getSender())) {
                            String[] onlineUsers = receivedMsg.getText().split(",");
                            Platform.runLater(() -> {
                                contactsList.getItems().clear(); 
                                contactsList.getItems().add("МЙ ПРОФІЛЬ");
                                contactsList.getItems().add("Ім'я: " + username);
                                contactsList.getItems().add("Хост: " + serverIp);
                                contactsList.getItems().add(""); 
                                
                                contactsList.getItems().add("👥 В мережі (" + onlineUsers.length + "):");
                                
                                for (String user : onlineUsers) {
                                    if (!user.trim().isEmpty() && user.equals(username)) {
                                        contactsList.getItems().add("👤 " + user + " (Ви - Збережені)");
                                        break;
                                    }
                                }
                                
                                for (String user : onlineUsers) {
                                    if (!user.trim().isEmpty() && !user.equals(username)) {
                                        contactsList.getItems().add("👤 " + user);
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
        dynamicChatBox.getChildren().clear();
        if (chatHistories.containsKey(selectedReceiver)) {
            dynamicChatBox.getChildren().addAll(chatHistories.get(selectedReceiver));
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
        File file = fileChooser.showOpenDialog(chatScrollPane.getScene().getWindow());

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

    private void appendMessage(String chatIdentifier, String time, String senderName, String text, boolean isOwn, boolean isSystem, String fileName, String base64File) {
        Platform.runLater(() -> {
            VBox messageContainer = new VBox(2);
            messageContainer.setMaxWidth(600); 

            Text timeNode = new Text("[" + time + "] ");
            timeNode.setFill(Color.GRAY);
            timeNode.setStyle("-fx-font-size: 14px;");

            Text senderNode = new Text(senderName);
            senderNode.setStyle("-fx-font-weight: bold; -fx-fill: " + (isOwn ? "#005A9E" : "#0A7E07") + "; -fx-font-size: 18px;");

            TextFlow headerFlow = new TextFlow(timeNode, senderNode);

            Node contentNode;
            if (fileName != null && !fileName.isEmpty()) {
                Hyperlink fileLink = new Hyperlink("📎 Завантажити: " + fileName);
                fileLink.setStyle("-fx-text-fill: #0000EE; -fx-underline: true; -fx-font-size: 18px;");
                fileLink.setOnAction(e -> saveFile(fileName, base64File));
                contentNode = fileLink;
            } else {
                Text textNode = new Text(text);
                textNode.setStyle("-fx-font-size: 18px; -fx-fill: #333333;");
                contentNode = textNode;
            }

            TextFlow contentFlow = new TextFlow(contentNode);

            HBox row = new HBox();
            
            if (isSystem) {
                row.setAlignment(Pos.CENTER);
                senderNode.setStyle("-fx-font-weight: bold; -fx-fill: gray; -fx-font-size: 16px;");
                headerFlow.setTextAlignment(TextAlignment.CENTER);
                contentFlow.setTextAlignment(TextAlignment.CENTER);
            } else if (isOwn) {
                row.setAlignment(Pos.CENTER_RIGHT); 
                headerFlow.setTextAlignment(TextAlignment.RIGHT);
                contentFlow.setTextAlignment(TextAlignment.RIGHT);
            } else {
                row.setAlignment(Pos.CENTER_LEFT); 
                headerFlow.setTextAlignment(TextAlignment.LEFT);
                contentFlow.setTextAlignment(TextAlignment.LEFT);
            }

            messageContainer.getChildren().addAll(headerFlow, contentFlow);
            row.getChildren().add(messageContainer);

            chatHistories.putIfAbsent(chatIdentifier, new ArrayList<>());
            chatHistories.get(chatIdentifier).add(row);

            if (chatIdentifier.equals(selectedReceiver)) {
                dynamicChatBox.getChildren().add(row);
            }
        });
    }

    private void saveFile(String fileName, String base64Data) {
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Зберегти файл");
        saveChooser.setInitialFileName(fileName);
        File saveFile = saveChooser.showSaveDialog(chatScrollPane.getScene().getWindow());
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