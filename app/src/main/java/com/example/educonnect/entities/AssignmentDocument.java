package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentDocument extends BaseEntity {
    private String filePath;
    private Double grade;
    private Timestamp createdAt;
    private String assignmentId;
    private String studentId;
}
