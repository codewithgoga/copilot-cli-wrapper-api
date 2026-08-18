package com.gd.copilotapi.copilot;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CopilotClient {

    private static final Logger log = LoggerFactory.getLogger(CopilotClient.class);

    private final CopilotCliService delegate;

    public CopilotClient(CopilotCliService delegate) {
        this.delegate = delegate;
    }

    public Mono<List<String>> listModels(String token) {
        log.debug("Delegating listModels call to CopilotCliService.");
        return delegate.listAvailableModels(token)
                .doOnSuccess(models -> log.info("Copilot model list retrieved. count={}", models == null ? 0 : models.size()));
    }

    public String resolveModel(String requestedModel) {
        String resolved = delegate.resolveModel(requestedModel);
        log.debug("Resolved model. requestedModel={}, resolvedModel={}", requestedModel, resolved);
        return resolved;
    }

    public Mono<String> complete(ChatCompletionRequest request, String token) {
        log.debug("Delegating complete call to CopilotCliService.");
        return delegate.complete(request, token)
                .doOnSuccess(content -> log.info("Copilot completion received. contentLength={}", content == null ? 0 : content.length()));
    }

    public Flux<String> stream(ChatCompletionRequest request, String token) {
        log.debug("Delegating stream call to CopilotCliService.");
        return delegate.streamAssistantDeltas(request, token)
                .doOnComplete(() -> log.info("Copilot delta stream completed."));
    }
}
