package com.gd.copilotapi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.gd.copilotapi.copilot.CopilotClient;
import com.gd.copilotapi.copilot.CopilotStreamHandler;
import com.gd.copilotapi.dto.openai.chat.ChatChoice;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionResponse;
import com.gd.copilotapi.dto.openai.chat.ChatMessage;
import com.gd.copilotapi.dto.openai.chat.Usage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionService.class);

    private final CopilotClient copilotClient;
    private final CopilotStreamHandler streamHandler;

    public ChatCompletionService(CopilotClient copilotClient, CopilotStreamHandler streamHandler) {
        this.copilotClient = copilotClient;
        this.streamHandler = streamHandler;
    }

    public Mono<ChatCompletionResponse> complete(ChatCompletionRequest request, String token) {
        String model = copilotClient.resolveModel(request.model());
        int messageCount = request.messages() == null ? 0 : request.messages().size();
        log.debug("Starting non-stream completion. model={}, messageCount={}", model, messageCount);
        return copilotClient.complete(request, token)
                .map(content -> new ChatCompletionResponse(
                        "chatcmpl-" + UUID.randomUUID(),
                        "chat.completion",
                        Instant.now().getEpochSecond(),
                        model,
                        List.of(new ChatChoice(0, new ChatMessage("assistant", content, null, null, null), "stop")),
                        new Usage(0, 0, 0)
                    ))
                    .doOnSuccess(response -> log.info("Non-stream completion generated. id={}, model={}", response.id(), response.model()));
    }

    public Flux<ServerSentEvent<String>> stream(ChatCompletionRequest request, String token) {
        String model = copilotClient.resolveModel(request.model());
                int messageCount = request.messages() == null ? 0 : request.messages().size();
                log.debug("Starting stream completion. model={}, messageCount={}", model, messageCount);
        Flux<String> deltas = copilotClient.stream(request, token)
                .bufferTimeout(12, Duration.ofMillis(80))
                .filter(parts -> !parts.isEmpty())
                .map(parts -> String.join("", parts));
                return streamHandler.toSse(deltas, model)
                    .doOnComplete(() -> log.info("Stream completion finished. model={}", model));
    }
}
