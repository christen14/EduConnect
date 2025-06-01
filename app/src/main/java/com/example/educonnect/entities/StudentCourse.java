package com.example.educonnect.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentCourse extends BaseEntity {
    private String studentEmail;
    private String courseCode;
}
