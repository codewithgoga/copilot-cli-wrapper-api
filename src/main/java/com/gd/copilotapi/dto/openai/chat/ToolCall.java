package com.gd.copilotapi.dto.openai.chat;

public record ToolCall(
        String id,
        String type,
        FunctionCall function
) {
}
