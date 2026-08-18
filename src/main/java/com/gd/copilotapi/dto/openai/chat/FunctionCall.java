package com.gd.copilotapi.dto.openai.chat;

public record FunctionCall(
        String name,
        String arguments
) {
}
