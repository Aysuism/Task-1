package com.example.email.listener;

import com.example.email.config.RabbitConfig;
import com.example.email.dto.MessageSavedEvent;
import com.example.email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MessageEventListener {

    private static final Logger log = LoggerFactory.getLogger(MessageEventListener.class);

    private final EmailService emailService;

    public MessageEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessageSaved(MessageSavedEvent event) {
        log.info("Received event {} for message {}", event.eventId(), event.messageId());

        if (event.recipientEmail() == null || !event.recipientEmail().contains("@")) {
            throw new AmqpRejectAndDontRequeueException("Invalid recipient: " + event.recipientEmail());
        }

        emailService.sendMessageSavedEmail(event);
        log.info("Email sent to {}", event.recipientEmail());
    }
}