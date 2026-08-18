package com.gd.copilotapi.dto.openai.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatMessage(
        String role,
        Object content,
        String name,
        @JsonProperty("tool_call_id")
        String toolCallId,
        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls
) {
}
