package com.gd.copilotapi.dto.openai.response;

import java.util.List;

public record ResponseOutput(
        String type,
        String id,
        String role,
        List<ResponseContent> content
) {
}
