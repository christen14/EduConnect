package com.example.educonnect.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User extends BaseEntity {
    private String email;
    private Long role;
    private String mode;
}
