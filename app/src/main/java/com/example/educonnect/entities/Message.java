package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message extends BaseEntity {
    private Timestamp createdAt;
    private Timestamp openedAt;
    private String subject;
    private String content;
    private String senderId;
    private String receiverId;
    private Boolean important;
}
