package com.gd.copilotapi.dto.openai.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gd.copilotapi.dto.openai.chat.ResponseFormat;

public record ResponseRequest(
        String model,
        Object input,
        String instructions,
        Boolean stream,
        Double temperature,
        @JsonProperty("max_output_tokens")
        Integer maxOutputTokens,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice")
        Object toolChoice,
        @JsonProperty("response_format")
        ResponseFormat responseFormat,
        Map<String, Object> metadata
) {
}
