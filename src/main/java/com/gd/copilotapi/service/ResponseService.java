package com.gd.copilotapi.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;
import com.gd.copilotapi.dto.openai.chat.ChatMessage;
import com.gd.copilotapi.dto.openai.embedding.EmbeddingRequest;
import com.gd.copilotapi.dto.openai.embedding.EmbeddingResponse;
import com.gd.copilotapi.dto.openai.response.ResponseContent;
import com.gd.copilotapi.dto.openai.response.ResponseObject;
import com.gd.copilotapi.dto.openai.response.ResponseOutput;
import com.gd.copilotapi.dto.openai.response.ResponseRequest;

import reactor.core.publisher.Mono;

@Service
public class ResponseService {

    private static final Logger log = LoggerFactory.getLogger(ResponseService.class);

    private final ChatCompletionService chatCompletionService;
    private final ObjectMapper objectMapper;

    private final Map<String, Map<String, Object>> files = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> batches = new ConcurrentHashMap<>();

    public ResponseService(ChatCompletionService chatCompletionService, ObjectMapper objectMapper) {
        this.chatCompletionService = chatCompletionService;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseObject> createResponse(ResponseRequest request, String token) {
        log.debug("Creating /responses output. model={}", request.model());
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.instructions())) {
            messages.add(new ChatMessage("system", request.instructions(), null, null, null));
        }
        messages.add(new ChatMessage("user", renderInput(request.input()), null, null, null));

        ChatCompletionRequest completionRequest = new ChatCompletionRequest(
                request.model(),
                messages,
                request.temperature(),
                null,
                1,
                false,
                null,
                null,
                request.maxOutputTokens(),
                null,
                null,
                null,
                null,
                null,
                null,
                request.toolChoice(),
                null,
                request.responseFormat(),
                request.metadata()
        );

        return chatCompletionService.complete(completionRequest, token)
                .map(result -> {
                    String text = result.choices().get(0).message().content() == null
                            ? ""
                            : String.valueOf(result.choices().get(0).message().content());
                    return new ResponseObject(
                            "resp-" + UUID.randomUUID(),
                            "response",
                            Instant.now().getEpochSecond(),
                            result.model(),
                            List.of(new ResponseOutput(
                                    "message",
                                    "msg-" + UUID.randomUUID(),
                                    "assistant",
                                    List.of(new ResponseContent("output_text", text))
                            )),
                            "completed",
                            new ResponseObject.Usage(0, 0, 0)
                    );
                })
                .doOnSuccess(response -> log.info("/responses completed. id={}, model={}", response.id(), response.model()));
    }

    public Mono<EmbeddingResponse> createEmbedding(EmbeddingRequest request) {
        String input = renderInput(request.input());
        Integer requestedDimensions = request.dimensions();
        int size = requestedDimensions != null && requestedDimensions > 0 ? requestedDimensions : 16;
        log.debug("Creating embedding. model={}, dimensions={}", request.model(), size);
        List<Double> embedding = buildDeterministicEmbedding(input, size);

        return Mono.just(new EmbeddingResponse(
                "list",
                List.of(new EmbeddingResponse.EmbeddingData("embedding", 0, embedding)),
                request.model() == null ? "text-embedding-3-small" : request.model(),
                new EmbeddingResponse.EmbeddingUsage(0, 0)
        ));
    }

    public Mono<Map<String, Object>> uploadFile(FilePart file, String purpose) {
        String fileId = "file-" + UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        long bytes = Math.max(file.headers().getContentLength(), 0);

        Map<String, Object> fileObject = new LinkedHashMap<>();
        fileObject.put("id", fileId);
        fileObject.put("object", "file");
        fileObject.put("bytes", bytes);
        fileObject.put("created_at", now);
        fileObject.put("filename", file.filename());
        fileObject.put("purpose", purpose);
        fileObject.put("status", "processed");

        files.put(fileId, fileObject);
        log.info("Stored file metadata. fileId={}, filename={}, bytes={}", fileId, file.filename(), bytes);
        return Mono.just(fileObject);
    }

    public Mono<List<Map<String, Object>>> listFiles() {
        log.debug("Listing stored files. count={}", files.size());
        return Mono.just(new ArrayList<>(files.values()));
    }

    public Mono<Map<String, Object>> getFile(String fileId) {
        log.debug("Fetching file metadata. fileId={}", fileId);
        Map<String, Object> file = files.get(fileId);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + fileId);
        }
        return Mono.just(file);
    }

    public Mono<Map<String, Object>> deleteFile(String fileId) {
        log.debug("Deleting file metadata. fileId={}", fileId);
        Map<String, Object> file = files.remove(fileId);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + fileId);
        }
        Map<String, Object> deleted = new LinkedHashMap<>(file);
        deleted.put("status", "deleted");
        return Mono.just(deleted);
    }

    public Mono<Map<String, Object>> moderate(Map<String, Object> request) {
        String input = renderInput(request.get("input")).toLowerCase();
        boolean flagged = input.contains("violence") || input.contains("self-harm") || input.contains("hate");
        log.debug("Moderation evaluated. flagged={}", flagged);

        Map<String, Object> categories = Map.of(
                "hate", input.contains("hate"),
                "self-harm", input.contains("self-harm"),
                "violence", input.contains("violence")
        );

        Map<String, Object> scores = Map.of(
                "hate", input.contains("hate") ? 0.9 : 0.0,
                "self-harm", input.contains("self-harm") ? 0.9 : 0.0,
                "violence", input.contains("violence") ? 0.9 : 0.0
        );

        return Mono.just(Map.of(
                "id", "modr-" + UUID.randomUUID(),
                "model", request.getOrDefault("model", "omni-moderation-latest"),
                "results", List.of(Map.of(
                        "flagged", flagged,
                        "categories", categories,
                        "category_scores", scores
                ))
        ));
    }

    public Mono<Map<String, Object>> imageGeneration(Map<String, Object> request) {
        String prompt = String.valueOf(request.getOrDefault("prompt", ""));
        log.debug("Generating placeholder image payload. promptLength={}", prompt.length());
        String payload = Base64.getEncoder().encodeToString(("placeholder-image:" + prompt).getBytes(StandardCharsets.UTF_8));

        return Mono.just(Map.of(
                "created", Instant.now().getEpochSecond(),
                "data", List.of(Map.of("b64_json", payload))
        ));
    }

    public Mono<Map<String, Object>> transcription(FilePart file, String model) {
        log.debug("Processing transcription compatibility request. filename={}, model={}", file.filename(), model);
        if (!StringUtils.hasText(model)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "model is required");
        }
        return Mono.just(Map.of("text", "Transcription is not backed by Copilot CLI in this deployment."));
    }

    public Mono<ResponseEntity<byte[]>> speech(Map<String, Object> request) {
        String input = String.valueOf(request.getOrDefault("input", ""));
        log.debug("Processing speech compatibility request. inputLength={}", input.length());
        if (!StringUtils.hasText(input)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input is required");
        }
        byte[] body = ("Speech synthesis is not backed by Copilot CLI: " + input).getBytes(StandardCharsets.UTF_8);
        return Mono.just(ResponseEntity.ok(body));
    }

    public Mono<Map<String, Object>> createBatch(Map<String, Object> request) {
        String batchId = "batch-" + UUID.randomUUID();
        log.debug("Creating batch metadata. batchId={}", batchId);

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("id", batchId);
        batch.put("object", "batch");
        batch.put("status", "in_progress");
        batch.put("input_file_id", request.getOrDefault("input_file_id", ""));
        batch.put("output_file_id", null);
        batch.put("error_file_id", null);
        batch.put("created_at", Instant.now().getEpochSecond());
        batch.put("endpoint", request.getOrDefault("endpoint", ""));
        batch.put("completion_window", request.getOrDefault("completion_window", ""));
        batch.put("metadata", request.get("metadata"));
        batches.put(batchId, batch);
        log.info("Batch created. batchId={}", batchId);

        return Mono.just(batch);
    }

    public Mono<List<Map<String, Object>>> listBatches() {
        log.debug("Listing batches. count={}", batches.size());
        return Mono.just(new ArrayList<>(batches.values()));
    }

    public Mono<Map<String, Object>> getBatch(String batchId) {
        log.debug("Fetching batch metadata. batchId={}", batchId);
        Map<String, Object> batch = batches.get(batchId);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + batchId);
        }
        return Mono.just(batch);
    }

    public Mono<Map<String, Object>> cancelBatch(String batchId) {
        log.debug("Cancelling batch metadata. batchId={}", batchId);
        Map<String, Object> batch = batches.get(batchId);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + batchId);
        }

        String status = String.valueOf(batch.get("status"));
        if ("completed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch cannot be cancelled in status: " + status);
        }

        batch.put("status", "cancelled");
        log.info("Batch cancelled. batchId={}", batchId);
        return Mono.just(batch);
    }

    private String renderInput(Object input) {
        if (input == null) {
            return "";
        }
        if (input instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            return String.valueOf(input);
        }
    }

    private List<Double> buildDeterministicEmbedding(String input, int size) {
        int seed = input.hashCode();
        List<Double> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int mixed = seed ^ (i * 0x45d9f3b);
            double normalized = ((mixed & 0x7fffffff) % 10000) / 10000.0;
            values.add(normalized);
        }
        return values;
    }
}
