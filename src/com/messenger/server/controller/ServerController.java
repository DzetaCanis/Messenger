package com.messenger.server.controller;

import com.messenger.shared.model.Message;
import com.messenger.shared.model.XmlParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerController {

    @FXML private TextArea serverLogsArea;
    @FXML private VBox monitoringBox; 
    @FXML private ListView<String> activeSessionsList;
    @FXML private TextField searchField;   

    private static final int PORT = 3128;
    private List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private ObservableList<String> activeNamesObservable = FXCollections.observableArrayList();
    private Map<String, String> serverAvatars = new ConcurrentHashMap<>();
    
    private Map<String, String> clientInfoMap = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        FilteredList<String> filteredData = new FilteredList<>(activeNamesObservable, s -> true);
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            filteredData.setPredicate(name -> {
                if (newV == null || newV.isEmpty()) return true;
                return name.toLowerCase().contains(newV.toLowerCase());
            });
        });
        activeSessionsList.setItems(filteredData);

        activeSessionsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setTooltip(null);
                } else {
                    setText(item);
                    String cleanName = item.replace("👤 ", "").trim();
                    if (clientInfoMap.containsKey(cleanName)) {
                        Tooltip tooltip = new Tooltip("Мережа: " + clientInfoMap.get(cleanName));
                        tooltip.setStyle("-fx-font-size: 14px;");
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

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
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                Platform.runLater(() -> logEvent("Сервер запущено на порту " + PORT + ". Очікування..."));

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    connectedClients.add(clientHandler);
                    Thread clientThread = new Thread(clientHandler);
                    clientThread.setDaemon(true); 
                    clientThread.start();
                }
            } catch (Exception e) {}
        });
        serverThread.setDaemon(true); 
        serverThread.start();
    }

    public void logEvent(String eventInfo) {
        serverLogsArea.appendText(eventInfo + "\n");
    }
    
    public void logSpy(String time, String sender, String receiver, String text, String fileName, String fileData, boolean isSystem) {
        Platform.runLater(() -> {
            TextFlow flow = new TextFlow();
            if (isSystem) {
                Text sysText = new Text("[" + time + "] " + text + "\n");
                sysText.setStyle("-fx-font-weight: bold; -fx-fill: #2e7d32; -fx-font-size: 14px;");
                flow.getChildren().add(sysText);
            } else {
                Text header = new Text("[" + time + "] " + sender + " -> " + receiver + ":\n");
                header.setStyle("-fx-font-weight: bold; -fx-fill: #333;");
                flow.getChildren().add(header);
                
                if (fileName != null && !fileName.isEmpty() && fileData != null && !fileData.isEmpty()) {
                    Hyperlink fileLink = new Hyperlink("📎: " + fileName + "\n");
                    fileLink.setStyle("-fx-text-fill: blue; -fx-font-size: 14px;");
                    fileLink.setOnAction(e -> openFileOnServer(fileName, fileData));
                    flow.getChildren().add(fileLink);
                } else {
                	Text content = new Text(text + "\n");
                	content.setStyle("-fx-font-size: 14px;");
                	addCopyMenu(content, text); 
                	flow.getChildren().add(content);
                    
                }
            }
            monitoringBox.getChildren().add(flow);
        });
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
    
    private void openFileOnServer(String fileName, String base64Data) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            File tempFile = new File(System.getProperty("java.io.tmpdir"), fileName);
            Files.write(tempFile.toPath(), decodedBytes);
            java.awt.Desktop.getDesktop().open(tempFile);
        } catch (Exception ex) {}
    }

    private void broadcastSystemMessage(String text) {
        try {
            Message sysMsg = new Message("Система", "ALL", text, getCurrentTime(), "", "");
            String xmlData = XmlParser.serialize(sysMsg);
            for (ClientHandler client : connectedClients) client.sendMessage(xmlData);
        } catch (Exception e) {}
    }

    private void kickUser(String targetName) {
        for (ClientHandler client : connectedClients) {
            if (client.clientName.equals(targetName)) {
                try {
                    Message kickMsg = new Message("SYSTEM", targetName, "KICK_USER", getCurrentTime(), "", "");
                    client.sendMessage(XmlParser.serialize(kickMsg));
                    Platform.runLater(() -> logEvent("Система: Користувача " + targetName + " заблоковано."));
                    logSpy(getCurrentTime(), "", "", "Користувача " + targetName + " видалено з сервера.", null, null, true);
                    new Thread(() -> { try { Thread.sleep(500); client.socket.close(); } catch(Exception e){} }).start();
                } catch (Exception e) {}
                break;
            }
        }
    }

    private void routeMessage(Message msg, String xmlMessage, ClientHandler sender) {
        String receiver = msg.getReceiver();
        String displayReceiver = (receiver == null || receiver.equals("ALL")) ? "ЗАГАЛЬНИЙ ЧАТ" : receiver;
        logSpy(msg.getTime(), sender.clientName, displayReceiver, msg.getText(), msg.getFileName(), msg.getFileData(), false);

        if (receiver == null || receiver.equals("ALL")) {
            for (ClientHandler client : connectedClients) {
                if (client != sender) client.sendMessage(xmlMessage);
            }
        } else {
            for (ClientHandler client : connectedClients) {
                if (client.clientName.equals(receiver)) {
                    client.sendMessage(xmlMessage);
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
            activeNamesObservable.clear();
            for (String name : activeNames) activeNamesObservable.add("👤 " + name);
            searchField.setPromptText("🔍 Пошук (Онлайн: " + activeNames.size() + ")...");
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
        public String ipInfo; 

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.ipInfo = socket.getInetAddress().getHostName() + " / " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            try { this.in = socket.getInputStream(); this.out = socket.getOutputStream(); } catch (Exception e) {}
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
                        
                        if (msg.getText().equals("CHANGE_NAME_REQUEST")) {
                            String oldName = msg.getFileName();
                            String newName = msg.getFileData(); 
                            boolean nameExists = connectedClients.stream().anyMatch(c -> c != this && newName.equals(c.clientName));
                            if (nameExists) {
                                Message errorMsg = new Message("SYSTEM", oldName, "RENAME_TAKEN", getCurrentTime(), "", "");
                                sendMessage(XmlParser.serialize(errorMsg));
                            } else {
                                clientName = newName;
                                clientInfoMap.put(newName, ipInfo);
                                clientInfoMap.remove(oldName);
                                
                                if (serverAvatars.containsKey(oldName)) {
                                    serverAvatars.put(newName, serverAvatars.remove(oldName));
                                }
                                updateActiveSessions();
                                Message nameChangedMsg = new Message("SYSTEM", "ALL", "NAME_CHANGED", getCurrentTime(), oldName, newName);
                                String broadcastXml = XmlParser.serialize(nameChangedMsg);
                                for (ClientHandler c : connectedClients) c.sendMessage(broadcastXml);
                                logSpy(getCurrentTime(), "", "", oldName + " змінив ім'я на " + newName, null, null, true);
                            }
                            continue;
                        }
                        
                        if (msg.getText().equals("CHANGE_AVATAR_REQUEST")) {
                            String user = msg.getFileName();
                            String base64Image = msg.getFileData();
                            serverAvatars.put(user, base64Image); 
                            Message avatarChangedMsg = new Message("SYSTEM", "ALL", "AVATAR_CHANGED", getCurrentTime(), user, base64Image);
                            String broadcastXml = XmlParser.serialize(avatarChangedMsg);
                            for (ClientHandler c : connectedClients) c.sendMessage(broadcastXml);
                            logSpy(getCurrentTime(), "", "", user + " оновив(ла) свою аватарку.", null, null, true);
                            continue;
                        }

                        if (clientName.equals("Невідомий") && msg.getSender() != null) {
                            String proposedName = msg.getSender();
                            boolean nameExists = connectedClients.stream().anyMatch(c -> c != this && proposedName.equals(c.clientName));
                            if (nameExists) {
                                Message errorMsg = new Message("SYSTEM", proposedName, "NAME_TAKEN", getCurrentTime(), "", "");
                                sendMessage(XmlParser.serialize(errorMsg));
                                Thread.sleep(500); return; 
                            }
                            clientName = proposedName;
                            clientInfoMap.put(clientName, ipInfo);
                            updateActiveSessions(); 
                            
                            Platform.runLater(() -> logEvent("Нове підключення: " + clientName + " (" + ipInfo + ")"));
                            broadcastSystemMessage("Користувач " + clientName + " приєднався до чату.");
                            
                            for (Map.Entry<String, String> entry : serverAvatars.entrySet()) {
                                Message avMsg = new Message("SYSTEM", clientName, "AVATAR_CHANGED", getCurrentTime(), entry.getKey(), entry.getValue());
                                sendMessage(XmlParser.serialize(avMsg));
                            }
                        } else {
                            routeMessage(msg, xmlMessage, this);
                        }
                    }
                }
            } catch (Exception e) {
            } finally {
                connectedClients.remove(this); 
                clientInfoMap.remove(clientName);
                updateActiveSessions(); 
                if (!clientName.equals("Невідомий")) {
                    Platform.runLater(() -> logEvent(clientName + " відключився."));
                    broadcastSystemMessage("Користувач " + clientName + " вийшов з мережі.");
                }
                try { socket.close(); } catch (Exception e) {}
            }
        }
        public void sendMessage(String msg) { try { out.write(msg.getBytes()); out.flush(); } catch (Exception e) {} }
    }
}