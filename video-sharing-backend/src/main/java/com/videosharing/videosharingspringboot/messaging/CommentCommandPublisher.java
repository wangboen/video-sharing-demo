package com.videosharing.videosharingspringboot.messaging;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommentCommandPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    private final String destination;

    public CommentCommandPublisher(RocketMQTemplate rocketMQTemplate,
                                   @Value("${comments.rocketmq.create-topic}") String topic,
                                   @Value("${comments.rocketmq.create-tag}") String tag) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.destination = topic + ":" + tag;
    }

    public void publish(CommentCreateCommand command) {
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, command);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new CommentPublishException("Comment create command was not accepted by RocketMQ");
            }
        } catch (CommentPublishException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CommentPublishException("Unable to publish comment create command", exception);
        }
    }
}
