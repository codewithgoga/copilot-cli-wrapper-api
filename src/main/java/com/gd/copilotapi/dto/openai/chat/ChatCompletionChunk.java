package com.gd.copilotapi.dto.openai.chat;

import java.util.List;

public record ChatCompletionChunk(
        String id,
        String object,
        Long created,
        String model,
        List<ChunkChoice> choices,
        Usage usage
) {
}
