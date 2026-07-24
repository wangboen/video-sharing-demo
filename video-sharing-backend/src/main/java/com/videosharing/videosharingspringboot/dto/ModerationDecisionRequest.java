package com.videosharing.videosharingspringboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModerationDecisionRequest(
        @NotBlank String decision,
        String reason,
        @NotNull Long version
) {
}
