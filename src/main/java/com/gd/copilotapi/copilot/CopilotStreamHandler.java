package com.gd.copilotapi.copilot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionChunk;
import com.gd.copilotapi.dto.openai.chat.ChunkChoice;
import com.gd.copilotapi.dto.openai.chat.Delta;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CopilotStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(CopilotStreamHandler.class);

    private final ObjectMapper objectMapper;

    public CopilotStreamHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> toSse(Flux<String> deltas, String model) {
        String completionId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        log.debug("Transforming Copilot deltas to SSE. completionId={}, model={}", completionId, model);

        return Flux.concat(
                Mono.just(event(json(firstChunk(completionId, created, model)))),
                deltas.map(delta -> event(json(contentChunk(completionId, created, model, delta)))),
                Mono.just(event(json(doneChunk(completionId, created, model)))),
                Mono.just(event("[DONE]"))
        ).doOnComplete(() -> log.info("SSE stream emitted done marker. completionId={}, model={}", completionId, model));
    }

    private ChatCompletionChunk firstChunk(String id, long created, String model) {
        return new ChatCompletionChunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta("assistant", null, null), null)),
                null
        );
    }

    private ChatCompletionChunk contentChunk(String id, long created, String model, String delta) {
        return new ChatCompletionChunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta(null, delta, null), null)),
                null
        );
    }

    private ChatCompletionChunk doneChunk(String id, long created, String model) {
        return new ChatCompletionChunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta(null, null, null), "stop")),
                null
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize stream chunk, falling back to error payload.", exception);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("error", "Failed to serialize stream chunk.");
            fallback.put("detail", exception.getMessage());
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (JsonProcessingException ignored) {
                log.error("Failed to serialize stream fallback payload.", ignored);
                return "{\"error\":\"serialization_failed\"}";
            }
        }
    }

    private ServerSentEvent<String> event(String payload) {
        return ServerSentEvent.<String>builder().data(payload).build();
    }
}
