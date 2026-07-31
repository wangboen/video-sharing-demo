package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommentServiceTest {
    @Mock private CommentRelationRepository relations;
    @Mock private CommentContentRepository contents;

    @Test
    void anonymizesOwnVisibleCommentWithoutChangingItsStatus() {
        UserCommentService service = new UserCommentService(relations, contents);
        User user = user(7L);
        CommentRelation relation = relation(10L, 7L);
        when(relations.findById(10L)).thenReturn(Optional.of(relation));

        service.anonymizeOwnComment(user, 10L);

        ArgumentCaptor<CommentRelation> saved = ArgumentCaptor.forClass(CommentRelation.class);
        verify(relations).save(saved.capture());
        assertThat(saved.getValue().isAnonymized()).isTrue();
        assertThat(saved.getValue().getAnonymizedAt()).isNotNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }

    @Test
    void rejectsAnotherUsersCommentWithoutChangingIt() {
        UserCommentService service = new UserCommentService(relations, contents);
        CommentRelation relation = relation(10L, 8L);
        when(relations.findById(10L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> service.anonymizeOwnComment(user(7L), 10L))
                .isInstanceOf(UserCommentService.CommentNotFoundException.class);

        assertThat(relation.isAnonymized()).isFalse();
        assertThat(relation.getAnonymizedAt()).isNull();
        verify(relations, never()).save(any());
    }

    @Test
    void listsOnlyNonAnonymizedCommentsWithOriginalMongoUsernameAndNoUserId() {
        UserCommentService service = new UserCommentService(relations, contents);
        PageRequest pageable = PageRequest.of(1, 5);
        CommentRelation relation = relation(10L, 7L);
        when(relations.findByUserIdAndAnonymizedFalse(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(relation), pageable, 6));
        when(contents.findAllById(List.of(10L))).thenReturn(List.of(content(10L, "alice")));

        var page = service.listOwnComments(user(7L), pageable);

        assertThat(page.getContent()).singleElement().satisfies(comment -> {
            assertThat(comment.commentId()).isEqualTo(10L);
            assertThat(comment.username()).isEqualTo("alice");
            assertThat(comment.anonymized()).isFalse();
        });
        assertThat(page.getContent().get(0).getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId");
        assertThat(page.getTotalElements()).isEqualTo(6);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private CommentRelation relation(Long commentId, Long userId) {
        CommentRelation relation = new CommentRelation();
        relation.setCommentId(commentId);
        relation.setUserId(userId);
        relation.setStatus(CommentStatus.VISIBLE);
        relation.setLikeCount(3L);
        relation.setCreatedAt(Instant.parse("2026-07-31T12:00:00Z"));
        return relation;
    }

    private CommentContentDocument content(Long commentId, String username) {
        CommentContentDocument content = new CommentContentDocument();
        content.setId(commentId);
        content.setUsername(username);
        content.setContent("hello");
        content.setContentType("text/plain");
        return content;
    }
}
