package com.videosharing.videosharingspringboot.messaging;

import com.videosharing.videosharingspringboot.service.CommentPersistenceService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "${comments.rocketmq.create-topic}",
        consumerGroup = "video-sharing-comment-create-consumer")
public class CommentCreateConsumer implements RocketMQListener<CommentCreateCommand> {
    private final CommentPersistenceService persistenceService;

    public CommentCreateConsumer(CommentPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void onMessage(CommentCreateCommand command) {
        persistenceService.persistCreateCommand(command);
    }
}
