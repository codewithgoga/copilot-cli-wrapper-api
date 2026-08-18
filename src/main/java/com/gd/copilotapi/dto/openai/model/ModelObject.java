package com.gd.copilotapi.dto.openai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelObject(
        String id,
        String object,
        Long created,
        @JsonProperty("owned_by")
        String ownedBy
) {
}
