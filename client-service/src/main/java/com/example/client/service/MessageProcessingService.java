package com.example.client.service;

import com.example.client.dto.MessageRequest;
import com.example.client.dto.ProcessedMessage;
import com.example.client.dto.StoredMessage;
import com.example.client.feign.StorageClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageProcessingService {

    private final StorageClient storageClient;

    public MessageProcessingService(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    public StoredMessage processAndStore(MessageRequest request) {
        validate(request);

        String author = capitalize(request.author().trim());
        String email = request.email().trim();
        String text = request.text().trim().replaceAll("\\s+", " ");
        int wordCount = text.split(" ").length;

        ProcessedMessage processed =
                new ProcessedMessage(author, email, text, wordCount, LocalDateTime.now());
        return storageClient.saveMessage(processed);
    }

    public List<StoredMessage> getAllMessages() {
        return storageClient.getAllMessages();
    }

    private void validate(MessageRequest request) {
        if (request == null
                || isBlank(request.author())
                || isBlank(request.email())
                || isBlank(request.text())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fields 'author', 'email' and 'text' are required"
            );
        }
        if (!request.email().contains("@")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'email' must look like an email address"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase()
                + value.substring(1).toLowerCase();
    }
}