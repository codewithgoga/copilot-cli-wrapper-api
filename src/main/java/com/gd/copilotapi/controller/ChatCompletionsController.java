package com.gd.copilotapi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionResponse;
import com.gd.copilotapi.service.ChatCompletionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
@Tag(name = "Chat", description = "OpenAI-compatible chat completion endpoints")
@SecurityRequirement(name = "githubPat")
public class ChatCompletionsController {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsController.class);

        private static final String NON_STREAM_EXAMPLE = """
                        {
                            "model": "auto",
                            "messages": [
                                {"role": "system", "content": "Use short answers."},
                                {"role": "user", "content": "Summarize stream vs non-stream responses."}
                            ],
                            "stream": false,
                            "temperature": 0.2,
                            "top_p": 1,
                            "n": 1,
                            "max_completion_tokens": 256,
                            "presence_penalty": 0,
                            "frequency_penalty": 0
                        }
                        """;

        private static final String STREAM_EXAMPLE = """
                        {
                            "model": "auto",
                            "messages": [
                                {"role": "user", "content": "Stream this response in chunks."}
                            ],
                            "stream": true,
                            "stream_options": {"include_usage": false}
                        }
                        """;

    private final ChatCompletionService chatCompletionService;

    public ChatCompletionsController(ChatCompletionService chatCompletionService) {
        this.chatCompletionService = chatCompletionService;
    }

        @Operation(
            summary = "Create chat completion",
            description = "OpenAI-compatible Chat Completions endpoint. Returns JSON when stream=false and SSE when stream=true.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ChatCompletionRequest.class),
                    examples = {
                        @ExampleObject(name = "nonStreaming", value = NON_STREAM_EXAMPLE),
                        @ExampleObject(name = "streaming", value = STREAM_EXAMPLE)
                    }
                )
            )
        )
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Non-streaming completion", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatCompletionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "429", description = "Rate limited"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "504", description = "Gateway timeout")
        })
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(@RequestBody ChatCompletionRequest request, @RequestHeader HttpHeaders headers) {
        String token = readToken(headers);
        int messageCount = request.messages() == null ? 0 : request.messages().size();
        log.info("Received chat completion request. stream={}, requestedModel={}, messageCount={}",
                Boolean.TRUE.equals(request.stream()), request.model(), messageCount);
        if (Boolean.TRUE.equals(request.stream())) {
            Flux<ServerSentEvent<String>> stream = chatCompletionService.stream(request, token);
            log.info("Returning SSE stream from /chat/completions.");
            return Mono.just(ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream));
        }
        return chatCompletionService.complete(request, token)
                .doOnSuccess(response -> log.info("Completed non-stream chat completion. responseModel={}", response.model()))
                .map(ResponseEntity::ok);
    }

        @Operation(
            summary = "Create streaming chat completion",
            description = "Explicit SSE endpoint for OpenAI-compatible chat streaming.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ChatCompletionRequest.class),
                    examples = @ExampleObject(name = "streaming", value = STREAM_EXAMPLE)
                )
            )
        )
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream", content = @Content(mediaType = "text/event-stream")),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
        })
    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatCompletionRequest request, @RequestHeader HttpHeaders headers) {
        int messageCount = request.messages() == null ? 0 : request.messages().size();
        log.info("Received explicit stream request. requestedModel={}, messageCount={}", request.model(), messageCount);
        return chatCompletionService.stream(request, readToken(headers));
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
