package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.service.CommentLikeService;
import com.videosharing.videosharingspringboot.service.HotCommentCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
class CommentRelationRepositoryTest {

    @Autowired
    private CommentRelationRepository comments;

    @Autowired
    private UserRepository users;

    @Autowired
    private VideoRepository videos;

    @Autowired
    private VideoCategoryRepository categories;

    @Autowired
    private CommentLikeRepository likes;

    @Autowired
    private TestEntityManager entityManager;

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

    @Test
    void returnsInitialVisibleCommentPageWithoutCursorBoundary() {
        User user = saveUser();
        Video video = saveVideo(user);
        save(111L, video, user, CommentStatus.VISIBLE, 1L, false, Instant.parse("2026-07-31T12:00:00Z"));
        save(112L, video, user, CommentStatus.VISIBLE, 1L, false, Instant.parse("2026-07-31T11:00:00Z"));
        save(113L, video, user, CommentStatus.HIDDEN, 1L, false, Instant.parse("2026-07-31T13:00:00Z"));

        List<Long> ids = comments.findVisibleByVideo(
                        video.getId(), PageRequest.of(0, 10))
                .stream()
                .map(CommentRelation::getCommentId)
                .toList();

        assertThat(ids).containsExactly(111L, 112L);
    }

    @Test
    void sequentialDuplicateLikeInsertsOnceAndIncrementsOnce() {
        Instant createdAt = Instant.parse("2026-07-31T12:00:00Z");
        User user = saveUser("comment-like-user");
        Video video = saveVideo(user, "comment-like-video.mp4");
        save(201L, video, user, CommentStatus.VISIBLE, 0L, false, createdAt);
        HotCommentCacheService hotCache = mock(HotCommentCacheService.class);
        CommentLikeService service = new CommentLikeService(comments, likes, hotCache);

        service.like(user, 201L);
        service.like(user, 201L);
        entityManager.flush();
        entityManager.clear();

        assertThat(likes.count()).isEqualTo(1);
        assertThat(comments.findById(201L).orElseThrow().getLikeCount()).isEqualTo(1L);
        verify(hotCache, times(1)).markVideoForHotRefresh(video.getId());
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
        return saveUser("comment-repository-user");
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded-password");
        return users.saveAndFlush(user);
    }

    private Video saveVideo(User user) {
        return saveVideo(user, "comment-repository.mp4");
    }

    private Video saveVideo(User user, String filePath) {
        Video video = new Video();
        video.setUser(user);
        video.setCategory(saveCategory());
        video.setTitle("Comment repository video");
        video.setFilePath(filePath);
        return videos.saveAndFlush(video);
    }

    private VideoCategory saveCategory() {
        VideoCategory category = new VideoCategory();
        category.setCode("comment-repository-category-" + System.nanoTime());
        category.setName("电影");
        return categories.saveAndFlush(category);
    }
}
