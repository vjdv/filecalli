package net.vjdv.filecalli.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface Utils {

    static String toRFC7231(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time).atOffset(ZoneOffset.UTC));
    }

    static String cleanFileName(String filename) {
        while (filename.contains("\\")) filename = filename.replace("\\", "/");
        while (filename.contains("/")) filename = filename.replace("/", "");
        while (filename.contains("..")) filename = filename.replace("..", ".");
        while (filename.contains(":")) filename = filename.replace(":", "");
        while (filename.contains("*")) filename = filename.replace("*", "");
        while (filename.contains("?")) filename = filename.replace("?", "");
        while (filename.contains("\"")) filename = filename.replace("\"", "");
        while (filename.contains("<")) filename = filename.replace("<", "");
        while (filename.contains(">")) filename = filename.replace(">", "");
        while (filename.contains("|")) filename = filename.replace("|", "");
        return filename;
    }

    static String mimeForExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) {
            return "application/octet-stream";
        }
        String ext = filename.substring(dot + 1);
        return switch (ext) {
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "md" -> "text/markdown";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "tar" -> "application/x-tar";
            case "gz" -> "application/gzip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogg" -> "video/ogg";
            case "avi" -> "video/x-msvideo";
            case "mpeg" -> "video/mpeg";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "tsv" -> "text/tab-separated-values";
            case "rtf" -> "application/rtf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt" -> "application/vnd.oasis.opendocument";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            default -> "application/octet-stream";
        };
    }

}
