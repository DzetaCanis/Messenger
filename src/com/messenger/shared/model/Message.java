package com.messenger.shared.model;

public class Message {
    private String sender;
    private String receiver;
    private String text;
    private String time;
    
    private String fileName;
    private String fileData; 

    public Message(String sender, String receiver, String text, String time, String fileName, String fileData) {
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
        this.time = time;
        this.fileName = fileName;
        this.fileData = fileData;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getText() { return text; }
    public String getTime() { return time; }
    public String getFileName() { return fileName; }
    public String getFileData() { return fileData; }
}