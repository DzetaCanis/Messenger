package com.messenger.shared.model;

public class XmlParser {

    public static String serialize(Message message) {
        // Додаємо порожні рядки, якщо файлу немає, щоб уникнути помилок null
        String fName = (message.getFileName() != null) ? message.getFileName() : "";
        String fData = (message.getFileData() != null) ? message.getFileData() : "";

        return "<message>" +
               "<sender>" + message.getSender() + "</sender>" +
               "<receiver>" + message.getReceiver() + "</receiver>" +
               "<time>" + message.getTime() + "</time>" +
               "<fileName>" + fName + "</fileName>" +
               "<fileData>" + fData + "</fileData>" +
               "<text>" + message.getText() + "</text>" +
               "</message>\n";
    }

    public static Message deserialize(String xml) {
        try {
            String sender = extractTag(xml, "sender");
            String receiver = extractTag(xml, "receiver");
            String time = extractTag(xml, "time");
            String fileName = extractTag(xml, "fileName");
            String fileData = extractTag(xml, "fileData");
            String text = extractTag(xml, "text");

            return new Message(sender, receiver, text, time, fileName, fileData);
        } catch (Exception e) {
            return new Message("Система", "ALL", "Помилка обробки повідомлення", "", "", "");
        }
    }

    // Допоміжний метод для зручного витягування даних між тегами
    private static String extractTag(String xml, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = xml.indexOf(startTag);
        int end = xml.indexOf(endTag);
        if (start != -1 && end != -1) {
            return xml.substring(start + startTag.length(), end);
        }
        return "";
    }
}