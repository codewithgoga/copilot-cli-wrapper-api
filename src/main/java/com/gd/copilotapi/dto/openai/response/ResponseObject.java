package com.gd.copilotapi.dto.openai.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResponseObject(
        String id,
        String object,
        @JsonProperty("created_at")
        Long createdAt,
        String model,
        List<ResponseOutput> output,
        String status,
        Usage usage
) {
    public record Usage(
            @JsonProperty("input_tokens")
            Integer inputTokens,
            @JsonProperty("output_tokens")
            Integer outputTokens,
            @JsonProperty("total_tokens")
            Integer totalTokens
    ) {
    }
}
