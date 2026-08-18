package com.gd.copilotapi.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gd.copilotapi.copilot.CopilotClient;
import com.gd.copilotapi.dto.openai.model.ModelListResponse;
import com.gd.copilotapi.dto.openai.model.ModelObject;

import reactor.core.publisher.Mono;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final CopilotClient copilotClient;

    public ModelService(CopilotClient copilotClient) {
        this.copilotClient = copilotClient;
    }

    public Mono<ModelListResponse> listModels(String token) {
        log.debug("Listing models via CopilotClient.");
        return copilotClient.listModels(token)
                .map(models -> new ModelListResponse("list", models.stream()
                        .map(model -> new ModelObject(model, "model", Instant.now().getEpochSecond(), "github-copilot"))
                        .toList()))
                .doOnSuccess(response -> log.info("Model listing complete. count={}", response.data() == null ? 0 : response.data().size()));
    }

    public Mono<ModelObject> getModel(String token, String modelId) {
        log.debug("Retrieving model metadata. modelId={}", modelId);
        return copilotClient.listModels(token)
                .flatMap(models -> {
                    if (!models.contains(modelId)) {
                        log.info("Requested model not available. modelId={}", modelId);
                        return Mono.empty();
                    }
                    log.info("Requested model found. modelId={}", modelId);
                    return Mono.just(new ModelObject(modelId, "model", Instant.now().getEpochSecond(), "github-copilot"));
                });
    }
}
