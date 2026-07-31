package com.videosharing.videosharingspringboot.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("comment_contents")
@Data
public class CommentContentDocument {
    @Id
    private Long id;

    private String username;
    private String content;
    private String contentType;
    private List<String> mentions;
    private List<String> attachments;
    private Integer contentVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
