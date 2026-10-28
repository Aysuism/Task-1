package com.example.email.service;

import com.example.email.dto.MessageSavedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendMessageSavedEmail(MessageSavedEvent event) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(event.recipientEmail());
        mail.setSubject("Your message was saved");
        mail.setText("Hello " + event.author() + ",\n\n"
                + "Your message (#" + event.messageId() + ", " + event.wordCount() + " words) was saved:\n\n"
                + event.text() + "\n\n"
                + "Saved at: " + event.savedAt() + "\n");
        mailSender.send(mail);
    }
}