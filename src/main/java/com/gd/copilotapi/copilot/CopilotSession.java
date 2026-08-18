package com.gd.copilotapi.copilot;

public record CopilotSession(
        String id,
        String model,
        long createdAt
) {
}
