package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentContentRepository extends MongoRepository<CommentContentDocument, Long> {
}
