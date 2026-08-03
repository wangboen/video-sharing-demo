package com.videosharing.videosharingspringboot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CreateCommentAcceptedResponse(String requestId,
                                            @JsonSerialize(using = ToStringSerializer.class) Long commentId,
                                            String status) {
}
