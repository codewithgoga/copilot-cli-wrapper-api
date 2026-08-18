package com.gd.copilotapi.dto.openai.chat;

public record ChatTool(
        String type,
        FunctionDefinition function
) {
}
