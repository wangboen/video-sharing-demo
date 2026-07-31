package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommentRelationRepositoryTest {

    @Autowired
    private CommentRelationRepository comments;

    @Autowired
    private UserRepository users;

    @Autowired
    private VideoRepository videos;

    @Test
    void returnsOnlyVisibleCommentsInExplicitTimeAndHotOrder() {
        Instant newest = Instant.parse("2026-07-31T12:00:00Z");
        Instant earlier = Instant.parse("2026-07-31T11:00:00Z");
        User user = saveUser();
        Video video = saveVideo(user);

        save(101L, video, user, CommentStatus.VISIBLE, 20L, false, newest);
        save(102L, video, user, CommentStatus.VISIBLE, 20L, false, newest);
        save(103L, video, user, CommentStatus.HIDDEN, 100L, false, Instant.parse("2026-07-31T13:00:00Z"));
        save(104L, video, user, CommentStatus.VISIBLE, 50L, true, earlier);
        save(105L, video, user, CommentStatus.VISIBLE, 9L, false, Instant.parse("2026-07-31T10:00:00Z"));

        List<Long> timeIds = comments.findVisibleByVideoAfterCursor(
                        video.getId(), newest.plusSeconds(1), Long.MAX_VALUE, PageRequest.of(0, 10))
                .stream()
                .map(CommentRelation::getCommentId)
                .toList();
        List<Long> cursorIds = comments.findVisibleByVideoAfterCursor(
                        video.getId(), newest, 102L, PageRequest.of(0, 10))
                .stream()
                .map(CommentRelation::getCommentId)
                .toList();
        List<Long> hotIds = comments.findTopHotVisible(video.getId(), 10L, PageRequest.of(0, 10))
                .stream()
                .map(CommentRelation::getCommentId)
                .toList();

        assertThat(timeIds).containsExactly(102L, 101L, 104L, 105L);
        assertThat(cursorIds).containsExactly(101L, 104L, 105L);
        assertThat(hotIds).containsExactly(104L, 102L, 101L);
    }

    private void save(Long commentId,
                      Video video,
                      User user,
                      CommentStatus status,
                      Long likeCount,
                      boolean anonymized,
                      Instant createdAt) {
        CommentRelation comment = new CommentRelation();
        comment.setCommentId(commentId);
        comment.setVideoId(video.getId());
        comment.setUserId(user.getId());
        comment.setStatus(status);
        comment.setLikeCount(likeCount);
        comment.setAnonymized(anonymized);
        comment.setCreatedAt(createdAt);
        comment.setUpdatedAt(createdAt);
        comments.saveAndFlush(comment);
    }

    private User saveUser() {
        User user = new User();
        user.setUsername("comment-repository-user");
        user.setPassword("encoded-password");
        return users.saveAndFlush(user);
    }

    private Video saveVideo(User user) {
        Video video = new Video();
        video.setUser(user);
        video.setTitle("Comment repository video");
        video.setFilePath("comment-repository.mp4");
        return videos.saveAndFlush(video);
    }
}
