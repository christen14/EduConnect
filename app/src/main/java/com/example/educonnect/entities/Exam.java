package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Exam extends BaseEntity {
    private Timestamp startTime;
    private Long duration;
    private String courseId;
}
