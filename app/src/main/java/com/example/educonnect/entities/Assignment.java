package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Assignment extends BaseEntity {
    private String title;
    private String content;
    private Timestamp dueAt;
    private Timestamp createdAt;
    private String courseId;
}
