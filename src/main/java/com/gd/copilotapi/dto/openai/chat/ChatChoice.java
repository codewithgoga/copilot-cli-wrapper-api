package com.gd.copilotapi.dto.openai.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatChoice(
        Integer index,
        ChatMessage message,
        @JsonProperty("finish_reason")
        String finishReason
) {
}
