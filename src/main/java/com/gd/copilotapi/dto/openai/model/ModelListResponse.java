package com.gd.copilotapi.dto.openai.model;

import java.util.List;

public record ModelListResponse(
        String object,
        List<ModelObject> data
) {
}
