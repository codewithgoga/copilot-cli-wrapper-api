package com.gd.copilotapi.dto.openai.error;

public record OpenAiErrorResponse(
        ErrorBody error
) {
    public record ErrorBody(
            String message,
            String type,
            String param,
            String code
    ) {
    }
}
