package com.example.storage.messaging;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageSavedEvent(
        UUID eventId,
        Long messageId,
        String author,
        String recipientEmail,
        String text,
        int wordCount,
        LocalDateTime savedAt
) {
}