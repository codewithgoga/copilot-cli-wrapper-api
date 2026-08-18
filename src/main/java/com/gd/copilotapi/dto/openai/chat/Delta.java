package com.gd.copilotapi.dto.openai.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Delta(
        String role,
        Object content,
        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls
) {
}
