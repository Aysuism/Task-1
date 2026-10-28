package com.example.storage.controller;

import com.example.storage.dto.MessageResponse;
import com.example.storage.dto.SaveMessageRequest;
import com.example.storage.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.example.storage.messaging.MessageEventPublisher;

import java.util.List;

@RestController
@RequestMapping("/api/storage/messages")
public class MessageController {

    private final MessageService messageService;
    private final MessageEventPublisher eventPublisher;

    public MessageController(MessageService messageService, MessageEventPublisher eventPublisher) {
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse save(@RequestBody SaveMessageRequest request) {
        MessageResponse saved = messageService.save(request);
        eventPublisher.publishMessageSaved(saved);
        return saved;
    }

    @GetMapping
    public List<MessageResponse> findAll() {
        return messageService.findAll();
    }
}