package com.example.storage.messaging;

import com.example.storage.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MessageEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MessageEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMessageSaved(MessageResponse saved) {
        MessageSavedEvent event = new MessageSavedEvent(
                UUID.randomUUID(), saved.id(), saved.author(), saved.email(),
                saved.text(), saved.wordCount(), saved.savedAt());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
        } catch (AmqpException e) {
            log.error("Could not publish event for message {}: {}", saved.id(), e.getMessage());
        }
    }
}