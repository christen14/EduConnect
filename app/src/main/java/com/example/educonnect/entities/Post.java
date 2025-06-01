package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Post extends BaseEntity {
    private Timestamp createdAt;
    private String title;
    private String content;
    private String courseId;
    private String userEmail;
}
