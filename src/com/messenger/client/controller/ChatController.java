package com.messenger.client.controller;

import com.messenger.shared.model.Message;
import com.messenger.shared.model.XmlParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatController {

    private String selectedReceiver = "ALL"; 
    private Map<String, List<Node>> chatHistories = new HashMap<>();
    private Map<String, String> userAvatars = new HashMap<>();
    private Set<String> unreadChats = new HashSet<>();
    private Map<String, Long> chatActivityMap = new ConcurrentHashMap<>();
    private List<String> currentActiveUsers = new ArrayList<>();

    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatBox; 
    @FXML private ListView<String> contactsList;
    @FXML private TextArea messageInput;
    @FXML private TextField searchField; 

    private ObservableList<String> contactsObservable = FXCollections.observableArrayList();
    private String serverIp;
    private int serverPort; 
    private String username;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        FilteredList<String> filteredData = new FilteredList<>(contactsObservable, s -> true);
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            filteredData.setPredicate(name -> {
                if (newV == null || newV.isEmpty()) return true;
                if (name.contains("Мій профіль") || name.contains("Ім'я:") || name.contains("Хост:") || name.contains("В мережі") || name.contains("Обрані") || name.contains("Загальний чат")) return true;
                return name.toLowerCase().contains(newV.toLowerCase());
            });
        });
        contactsList.setItems(filteredData);
        chatBox.heightProperty().addListener((o, oldV, newV) -> Platform.runLater(() -> chatScrollPane.setVvalue(1.0)));

        contactsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    if (item.contains("Мій профіль") || item.contains("Ім'я:") || item.contains("Хост:") || item.trim().isEmpty()) {
                        setText(item); setGraphic(null);
                        setStyle("-fx-alignment: center; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #555555; -fx-background-color: transparent;");
                        setMouseTransparent(false); 
                    } else if (item.contains("В мережі (")) {
                        setText(item); setGraphic(null);
                        setStyle("-fx-alignment: center-left; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #555555; -fx-background-color: transparent;");
                        setMouseTransparent(false); 
                    } else if (item.contains("Обрані")) {
                        Node avatarNode = createAvatarNode(username); 
                        Label nameLabel = new Label("Обрані");
                        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");
                        
                        HBox cellBox = new HBox(10, avatarNode, nameLabel);
                        cellBox.setAlignment(Pos.CENTER_LEFT);
                        setText(null); setGraphic(cellBox); setStyle("-fx-background-color: transparent;"); setMouseTransparent(false); 
                        
                    } else if (item.contains("Загальний чат")) {
                        Label nameLabel = new Label("@ Загальний чат (ALL)");
                        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #005A9E;  ");
                        HBox cellBox = new HBox(10, nameLabel);
                        cellBox.setAlignment(Pos.CENTER_LEFT);
                        setText(null); setGraphic(cellBox); setStyle("-fx-background-color: transparent;"); setMouseTransparent(false); 
                    } else {
                        String cleanName = item.replace("👤 ", "").trim();
                        Node avatarNode = createAvatarNode(cleanName);
                        Label nameLabel = new Label(cleanName); 
                        nameLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;"); 
                        
                        HBox cellBox = new HBox(10, avatarNode, nameLabel);
                        cellBox.setAlignment(Pos.CENTER_LEFT);
                        
                        if (unreadChats.contains(cleanName)) {
                            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                            cellBox.getChildren().addAll(spacer, new Circle(6, Color.web("#ff5252")));
                        }
                        
                        setText(null); setGraphic(cellBox); setStyle("-fx-background-color: transparent;"); setMouseTransparent(false); 
                    }
                }
            }
        });

        contactsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.contains("В мережі") || newValue.contains("Мій профіль") || newValue.contains("Ім'я:") || newValue.contains("Хост:")) return;
            
            if (newValue.startsWith("👤 ")) {
                selectedReceiver = newValue.replace("👤 ", "").trim();
                messageInput.setPromptText("Приватне повідомлення для " + selectedReceiver + "...");
            } else if (newValue.contains("Загальний чат")) {
                selectedReceiver = "ALL";
                messageInput.setPromptText("Написати всім...");
            } else if (newValue.contains("Обрані")) {
                selectedReceiver = username;
                messageInput.setPromptText("Збережені повідомлення...");
            }
            
            if (unreadChats.remove(selectedReceiver)) {
                Platform.runLater(() -> contactsList.refresh());
            }
            
            refreshChatBox();
        });

        messageInput.textProperty().addListener((o, oldV, newV) -> {
            if (newV.length() > 1000) messageInput.setText(oldV); 
            else Platform.runLater(() -> {
                if (messageInput.getScene() != null && messageInput.getScene().getWindow() != null) {
                    ((Stage)messageInput.getScene().getWindow()).setTitle("ICQ Клієнт - Чат (" + username + ") | Символів: " + messageInput.getText().length() + "/1000");
                }
            });
        });

        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume(); handleSendMessage(null); 
            }
        });
    }
    private Node createAvatarNode(String targetName) {
        Circle avatar = new Circle(15); 
        if (userAvatars.containsKey(targetName)) {
            try {
                byte[] imgData = Base64.getDecoder().decode(userAvatars.get(targetName));
                Image img = new Image(new ByteArrayInputStream(imgData));
                avatar.setFill(new ImagePattern(img));
                return avatar;
            } catch (Exception e) {}
        }
        avatar.setFill(Color.web("#81d4fa"));
        String initial = targetName.isEmpty() ? "?" : targetName.substring(0, 1).toUpperCase();
        Text initText = new Text(initial);
        initText.setStyle("-fx-font-weight: bold; -fx-fill: white;");
        return new StackPane(avatar, initText);
    }

    private void updateContactsView() {
        Platform.runLater(() -> {
            String currentSelection = contactsList.getSelectionModel().getSelectedItem();
            
            List<String> newOrder = new ArrayList<>();
            newOrder.add("Мій профіль");
            newOrder.add("Ім'я: " + username);
            newOrder.add("Хост: " + serverIp);
            newOrder.add("👥 В мережі (" + currentActiveUsers.size() + "):");
            newOrder.add("Обрані");
            newOrder.add("Загальний чат");
            
            List<String> sortedOthers = new ArrayList<>(currentActiveUsers);
            sortedOthers.remove(username); 
            
            sortedOthers.sort((a, b) -> {
                long timeA = chatActivityMap.getOrDefault(a, 0L);
                long timeB = chatActivityMap.getOrDefault(b, 0L);
                return Long.compare(timeB, timeA);
            });
            
            for (String user : sortedOthers) {
                newOrder.add("👤 " + user);
            }
            
            if (!contactsObservable.equals(newOrder)) {
                contactsObservable.setAll(newOrder);
                if (currentSelection != null && contactsObservable.contains(currentSelection)) {
                    contactsList.getSelectionModel().select(currentSelection);
                }
            }
        });
    }

    @FXML
    private void handleEditName() {
        TextInputDialog dialog = new TextInputDialog(username);
        dialog.setTitle("Зміна імені");
        dialog.setHeaderText("Введіть нове ім'я (до 20 символів):");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty() && newName.length() <= 20 && !newName.equals(username)) {
                try {
                    Message msg = new Message(username, "ALL", "CHANGE_NAME_REQUEST", getCurrentTime(), username, newName.trim());
                    out.write(XmlParser.serialize(msg).getBytes());
                    out.flush();
                } catch (Exception e) {}
            }
        });
    }

    @FXML
    private void handleEditAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть картинку (JPG, PNG)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(chatScrollPane.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                    Message msg = new Message(username, "ALL", "CHANGE_AVATAR_REQUEST", getCurrentTime(), username, base64Data);
                    out.write(XmlParser.serialize(msg).getBytes());
                    out.flush();
                } catch (Exception ex) {}
            }).start();
        }
    }

    public void initData(String ip, int port, String username) {
        this.serverIp = ip;
        this.serverPort = port;
        this.username = username;
        connectToServer();
    }
    
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(serverIp, serverPort); 
                out = socket.getOutputStream();
                in = socket.getInputStream();
                
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
                                    alert.setTitle("Доступ закрито");
                                    alert.setHeaderText("Порушення правил використання чату");
                                    VBox content = new VBox(10);
                                    content.getChildren().add(new javafx.scene.control.Label("Вам було закрито доступ до сервера за порушення правил."));
                                    Hyperlink link = new Hyperlink("📖 Читати правила використання чату");
                                    link.setStyle("-fx-font-size: 14px; -fx-text-fill: blue;");
                                    link.setOnAction(e -> {
                                        try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://youtu.be/FfpSQNaoK-U?si=WBP0aBJAGbBk7AC8")); } 
                                        catch (Exception ex) {}
                                    });
                                    content.getChildren().add(link);
                                    alert.getDialogPane().setContent(content);
                                    alert.showAndWait();
                                    System.exit(0);
                                });
                                return;
                            } else if ("NAME_TAKEN".equals(receivedMsg.getText())) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Помилка"); alert.setHeaderText(null);
                                    alert.setContentText("Це ім'я вже зайняте!");
                                    alert.showAndWait();
                                    try {
                                        socket.close();
                                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/messenger/client/view/login-view.fxml"));
                                        Parent root = loader.load();
                                        Stage stage = (Stage) chatScrollPane.getScene().getWindow();
                                        stage.setScene(new Scene(root));
                                        stage.setTitle("ICQ Клієнт - Вхід");
                                        stage.centerOnScreen();
                                    } catch (Exception e) {
                                        // Рассылаем уведомление во ВСЕ чаты
                                        for (String chatTab : chatHistories.keySet()) {
                                            appendMessage(chatTab, getCurrentTime(), "Система", "З'єднання з сервером втрачено!", false, true, null, null);
                                        }
                                    }
                                });
                                return; 
                            } else if ("RENAME_TAKEN".equals(receivedMsg.getText())) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.WARNING);
                                    alert.setTitle("Помилка перейменування");
                                    alert.setHeaderText(null);
                                    alert.setContentText("Це ім'я вже зайняте іншим користувачем!");
                                    alert.showAndWait();
                                });
                                continue;
                            } else if ("NAME_CHANGED".equals(receivedMsg.getText())) {
                                String oldName = receivedMsg.getFileName();
                                String newName = receivedMsg.getFileData();
                                
                                Platform.runLater(() -> {
                                    if (oldName.equals(username)) {
                                        username = newName;
                                        if (messageInput.getScene() != null && messageInput.getScene().getWindow() != null) {
                                            ((Stage)messageInput.getScene().getWindow()).setTitle("ICQ Клієнт - Чат (" + username + ") | Символів: " + messageInput.getText().length() + "/1000");
                                        }
                                    }
                                    
                                    if (chatHistories.containsKey(oldName)) chatHistories.put(newName, chatHistories.remove(oldName));
                                    if (userAvatars.containsKey(oldName)) userAvatars.put(newName, userAvatars.remove(oldName));
                                    if (unreadChats.contains(oldName)) { unreadChats.remove(oldName); unreadChats.add(newName); }
                                    if (chatActivityMap.containsKey(oldName)) chatActivityMap.put(newName, chatActivityMap.remove(oldName));
                                    
                                    if (selectedReceiver.equals(oldName)) {
                                        selectedReceiver = newName;
                                        messageInput.setPromptText("Приватне повідомлення для " + selectedReceiver + "...");
                                    }
                                    
                                    for (String chatTab : chatHistories.keySet()) {
                                        appendMessage(chatTab, receivedMsg.getTime(), "Система", oldName + " змінив ім'я на " + newName, false, true, null, null);
                                    }
                                    updateContactsView();
                                });
                                continue;
                            } else if ("AVATAR_CHANGED".equals(receivedMsg.getText())) {
                                String updatedUser = receivedMsg.getFileName();
                                String newAvatarBase64 = receivedMsg.getFileData();
                                userAvatars.put(updatedUser, newAvatarBase64);
                                Platform.runLater(() -> {
                                    updateContactsView();
                                    for (String chatTab : chatHistories.keySet()) {
                                        appendMessage(chatTab, receivedMsg.getTime(), "Система", updatedUser + " оновив(ла) свою аватарку.", false, true, null, null);
                                    }
                                });
                                continue;
                            }
                        }

                        if ("Система".equals(receivedMsg.getSender())) {
                            Platform.runLater(() -> {
                                for (String chatTab : chatHistories.keySet()) {
                                    appendMessage(chatTab, receivedMsg.getTime(), "Система", receivedMsg.getText(), false, true, null, null);
                                }
                                if (!chatHistories.containsKey("ALL")) {
                                    appendMessage("ALL", receivedMsg.getTime(), "Система", receivedMsg.getText(), false, true, null, null);
                                }
                            });
                            continue;
                        }

                        if ("SYSTEM_CONTACT_LIST".equals(receivedMsg.getSender())) {
                            String[] onlineUsers = receivedMsg.getText().split(",");
                            currentActiveUsers.clear();
                            for (String u : onlineUsers) {
                                if (!u.trim().isEmpty()) currentActiveUsers.add(u.trim());
                            }
                            updateContactsView();
                        } else {
                            if (!receivedMsg.getSender().equals(username)) {
                                String chatTab = receivedMsg.getReceiver().equals("ALL") ? "ALL" : receivedMsg.getSender();
                                appendMessage(chatTab, receivedMsg.getTime(), receivedMsg.getSender(), receivedMsg.getText(), false, false, receivedMsg.getFileName(), receivedMsg.getFileData());
                                
                                if (!chatTab.equals(selectedReceiver)) {
                                    unreadChats.add(chatTab);
                                    Platform.runLater(() -> contactsList.refresh());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
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
            } catch (Exception e) {}
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
                } catch (Exception ex) {}
            }).start();
        }
    }

    private void appendMessage(String chatIdentifier, String time, String senderName, String text, boolean isOwn, boolean isSystem, String fileName, String base64File) {
        chatActivityMap.put(chatIdentifier, System.currentTimeMillis());
        updateContactsView();

        Platform.runLater(() -> {
            VBox messageContainer = new VBox(2);
            messageContainer.setMaxWidth(600); 

            Text timeNode = new Text("[" + time + "] ");
            timeNode.setFill(Color.GRAY);
            timeNode.setStyle("-fx-font-size: 14px;");

            Text senderNode = new Text(senderName);
            senderNode.setStyle("-fx-font-weight: bold; -fx-fill: " + (isOwn ? "#005A9E" : "#0A7E07") + "; -fx-font-size: 18px;");

            TextFlow headerFlow;
            if (isOwn) {
                headerFlow = new TextFlow(timeNode, senderNode);
                headerFlow.setTextAlignment(TextAlignment.RIGHT);
            } else if (isSystem) {
                headerFlow = new TextFlow(timeNode, senderNode);
                headerFlow.setTextAlignment(TextAlignment.CENTER);
            } else {
                headerFlow = new TextFlow(senderNode, new Text(" "), timeNode);
                headerFlow.setTextAlignment(TextAlignment.LEFT);
            }

            Node contentNode;
            if (fileName != null && !fileName.isEmpty() && base64File != null && !base64File.isEmpty()) {
                Hyperlink fileLink = new Hyperlink("📎: " + fileName);
                fileLink.setStyle("-fx-text-fill: #0000EE; -fx-underline: true; -fx-font-size: 18px;");
                fileLink.setOnAction(e -> saveFile(fileName, base64File));
                contentNode = fileLink;
            } else {
                Text textNode = new Text(text);
                textNode.setStyle("-fx-font-size: 18px; -fx-fill: #333333;");

                addCopyMenu(textNode, text);
                
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
                contentFlow.setTextAlignment(TextAlignment.RIGHT);
            } else {
                row.setAlignment(Pos.CENTER_LEFT); 
                headerFlow.setTextAlignment(TextAlignment.LEFT);
            }

            messageContainer.getChildren().addAll(headerFlow, contentFlow);
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
    
    private void addCopyMenu(Node node, String textToCopy) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копіювати");
        copyItem.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);
        });
        contextMenu.getItems().add(copyItem);
        node.setOnContextMenuRequested(e -> contextMenu.show(node, e.getScreenX(), e.getScreenY()));
    }
    
    
}