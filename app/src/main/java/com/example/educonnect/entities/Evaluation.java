package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Evaluation extends BaseEntity {
    private Long q1;
    private Long q2;
    private Long q3;
    private Long q4;
    private Long q5;
    private String comment;
    private Timestamp submittedAt;
    private String courseId;
    private String studentEmail;
}
