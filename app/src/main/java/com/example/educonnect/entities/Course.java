package com.example.educonnect.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Course extends BaseEntity {
    private String code;
    private String name;
    private Long semester;
    private String professorId;
}
