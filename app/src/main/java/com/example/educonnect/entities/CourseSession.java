package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseSession extends BaseEntity {
    private Timestamp startTime;
    private String room;
    private String courseId;
}
