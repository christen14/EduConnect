package com.example.educonnect.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentExam extends BaseEntity {
    private String room;
    private Long seat;
    private String examId;
    private String studentEmail;
}
