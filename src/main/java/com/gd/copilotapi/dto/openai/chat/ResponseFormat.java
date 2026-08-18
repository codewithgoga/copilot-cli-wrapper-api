package com.gd.copilotapi.dto.openai.chat;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResponseFormat(
        String type,
        @JsonProperty("json_schema")
        Map<String, Object> jsonSchema
) {
}
