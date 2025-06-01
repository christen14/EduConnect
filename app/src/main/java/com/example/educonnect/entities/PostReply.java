package com.example.educonnect.entities;

import com.google.firebase.Timestamp;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostReply extends BaseEntity {
    private Timestamp createdAt;
    private String content;
    private String postId;
    private String userEmail;
    private Long upvotes;
    private List<String> upvotedBy;
}
