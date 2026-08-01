package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "videos")
public class VideoSearchDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String uploader;

    @Field(type = FieldType.Keyword)
    private String thumbnailPath;

    @Field(type = FieldType.Keyword)
    private String filePath;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime uploadDate;

    @Field(type = FieldType.Long)
    private Long views;

    @Field(type = FieldType.Keyword)
    private String status;

    public static VideoSearchDocument from(Video video) {
        VideoSearchDocument document = new VideoSearchDocument();
        document.id = video.getId();
        document.title = video.getTitle();
        document.description = video.getDescription();
        document.uploader = video.getUser() == null ? null : video.getUser().getUsername();
        document.thumbnailPath = video.getThumbnailPath();
        document.filePath = video.getFilePath();
        document.uploadDate = video.getUploadDate();
        document.views = video.getViews();
        document.status = video.getStatus() == null ? null : video.getStatus().name();
        return document;
    }

    public Long getId() {
        return id;
    }
}
