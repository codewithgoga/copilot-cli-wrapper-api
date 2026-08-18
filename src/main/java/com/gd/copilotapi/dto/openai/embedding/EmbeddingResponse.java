package com.gd.copilotapi.dto.openai.embedding;

import java.util.List;

public record EmbeddingResponse(
        String object,
        List<EmbeddingData> data,
        String model,
        EmbeddingUsage usage
) {
    public record EmbeddingData(
            String object,
            Integer index,
            List<Double> embedding
    ) {
    }

    public record EmbeddingUsage(
            Integer prompt_tokens,
            Integer total_tokens
    ) {
    }
}
