package com.gd.copilotapi.dto.openai.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChunkChoice(
        Integer index,
        Delta delta,
        @JsonProperty("finish_reason")
        String finishReason
) {
}
