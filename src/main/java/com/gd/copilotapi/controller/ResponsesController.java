package com.gd.copilotapi.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.gd.copilotapi.dto.openai.embedding.EmbeddingRequest;
import com.gd.copilotapi.dto.openai.embedding.EmbeddingResponse;
import com.gd.copilotapi.dto.openai.response.ResponseObject;
import com.gd.copilotapi.dto.openai.response.ResponseRequest;
import com.gd.copilotapi.service.ResponseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
@Tag(name = "Compatibility", description = "OpenAI-compatible responses and optional compatibility endpoints")
@SecurityRequirement(name = "githubPat")
public class ResponsesController {

    private static final Logger log = LoggerFactory.getLogger(ResponsesController.class);

    private final ResponseService responseService;

    public ResponsesController(ResponseService responseService) {
        this.responseService = responseService;
    }

    @Operation(summary = "Create response", description = "Optional newer response-oriented compatibility endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Response generated", content = @Content(schema = @Schema(implementation = ResponseObject.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "429", description = "Rate limited"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping("/responses")
    public Mono<ResponseObject> responses(@RequestBody ResponseRequest request, @RequestHeader HttpHeaders headers) {
        log.info("Received /responses request. model={}", request.model());
        return responseService.createResponse(request, readToken(headers));
    }

    @Operation(summary = "Create embeddings", description = "Optional embeddings compatibility endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Embeddings returned", content = @Content(schema = @Schema(implementation = EmbeddingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    })
    @PostMapping("/embeddings")
    public Mono<EmbeddingResponse> embeddings(@RequestBody EmbeddingRequest request) {
        log.info("Received /embeddings request. model={}", request.model());
        return responseService.createEmbedding(request);
    }

    @Operation(summary = "Upload file", description = "Upload file metadata for compatibility workflows.")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadFile(@RequestPart("file") FilePart file, @RequestPart("purpose") String purpose) {
        log.info("Received /files upload. filename={}, purpose={}", file.filename(), purpose);
        return responseService.uploadFile(file, purpose);
    }

    @Operation(summary = "List files", description = "List uploaded files.")
    @GetMapping("/files")
    public Mono<?> listFiles() {
        log.info("Received /files list request.");
        return responseService.listFiles();
    }

    @Operation(summary = "Retrieve file", description = "Retrieve file metadata by id.")
    @GetMapping("/files/{file_id}")
    public Mono<Map<String, Object>> getFile(@PathVariable("file_id") String fileId) {
        log.info("Received /files/{{file_id}} request. fileId={}", fileId);
        return responseService.getFile(fileId);
    }

    @Operation(summary = "Delete file", description = "Delete uploaded file metadata by id.")
    @DeleteMapping("/files/{file_id}")
    public Mono<Map<String, Object>> deleteFile(@PathVariable("file_id") String fileId) {
        log.info("Received /files/{{file_id}} delete request. fileId={}", fileId);
        return responseService.deleteFile(fileId);
    }

    @Operation(summary = "Moderate content", description = "Optional moderation compatibility endpoint.")
    @PostMapping("/moderations")
    public Mono<Map<String, Object>> moderations(@RequestBody Map<String, Object> request) {
        log.info("Received /moderations request.");
        return responseService.moderate(request);
    }

    @Operation(summary = "Generate image", description = "Optional image generation compatibility endpoint.")
    @PostMapping("/images/generations")
    public Mono<Map<String, Object>> imageGeneration(@RequestBody Map<String, Object> request) {
        log.info("Received /images/generations request.");
        return responseService.imageGeneration(request);
    }

    @Operation(summary = "Transcribe audio", description = "Optional speech-to-text compatibility endpoint.")
    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> transcriptions(@RequestPart("file") FilePart file, @RequestPart("model") String model) {
        log.info("Received /audio/transcriptions request. filename={}, model={}", file.filename(), model);
        return responseService.transcription(file, model);
    }

    @Operation(summary = "Generate speech", description = "Optional text-to-speech compatibility endpoint.")
    @PostMapping(value = "/audio/speech", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<byte[]>> speech(@RequestBody Map<String, Object> request) {
        log.info("Received /audio/speech request.");
        return responseService.speech(request)
                .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(body.getBody()));
    }

    @Operation(summary = "Create batch", description = "Optional asynchronous batch processing endpoint.")
    @PostMapping("/batches")
    public Mono<Map<String, Object>> createBatch(@RequestBody Map<String, Object> request) {
        log.info("Received /batches create request.");
        return responseService.createBatch(request);
    }

    @Operation(summary = "List batches", description = "List batch jobs.")
    @GetMapping("/batches")
    public Mono<?> listBatches() {
        log.info("Received /batches list request.");
        return responseService.listBatches();
    }

    @Operation(summary = "Get batch", description = "Retrieve a batch by id.")
    @GetMapping("/batches/{batch_id}")
    public Mono<Map<String, Object>> getBatch(@PathVariable("batch_id") String batchId) {
        log.info("Received /batches/{{batch_id}} request. batchId={}", batchId);
        return responseService.getBatch(batchId);
    }

    @Operation(summary = "Cancel batch", description = "Cancel a running batch.")
    @PostMapping("/batches/{batch_id}/cancel")
    public Mono<Map<String, Object>> cancelBatch(@PathVariable("batch_id") String batchId) {
        log.info("Received /batches/{{batch_id}}/cancel request. batchId={}", batchId);
        return responseService.cancelBatch(batchId);
    }

    private String readToken(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        String fallback = headers.getFirst("X-GitHub-Token");
        return fallback == null ? "" : fallback.trim();
    }
}
