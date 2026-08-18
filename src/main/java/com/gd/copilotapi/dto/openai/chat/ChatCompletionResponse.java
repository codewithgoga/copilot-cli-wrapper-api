package com.gd.copilotapi.dto.openai.chat;

import java.util.List;

public record ChatCompletionResponse(
        String id,
        String object,
        Long created,
        String model,
        List<ChatChoice> choices,
        Usage usage
) {
}
