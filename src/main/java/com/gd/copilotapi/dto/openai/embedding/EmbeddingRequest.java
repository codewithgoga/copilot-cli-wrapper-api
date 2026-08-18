package com.gd.copilotapi.dto.openai.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmbeddingRequest(
        String model,
        Object input,
        @JsonProperty("encoding_format")
        String encodingFormat,
        Integer dimensions,
        String user
) {
}
