package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseDocument extends BaseEntity {
    private String courseId;
    private String folder;
    private String filePath;
    private Timestamp uploadedAt;
}
