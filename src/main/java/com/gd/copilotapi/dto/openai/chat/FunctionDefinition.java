package com.gd.copilotapi.dto.openai.chat;

import java.util.Map;

public record FunctionDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {
}
