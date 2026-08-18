package com.gd.copilotapi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gd.copilotapi.dto.openai.model.ModelListResponse;
import com.gd.copilotapi.dto.openai.model.ModelObject;
import com.gd.copilotapi.service.ModelService;

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
@Tag(name = "Models", description = "OpenAI-compatible model catalog endpoints")
@SecurityRequirement(name = "githubPat")
public class ModelsController {

    private static final Logger log = LoggerFactory.getLogger(ModelsController.class);

    private final ModelService modelService;

    public ModelsController(ModelService modelService) {
        this.modelService = modelService;
    }

    @Operation(summary = "List models", description = "List models exposed by the Copilot-backed gateway.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model list", content = @Content(schema = @Schema(implementation = ModelListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    })
    @GetMapping("/models")
    public Mono<ModelListResponse> models(@RequestHeader HttpHeaders headers) {
        String token = readToken(headers);
        log.info("Received models request.");
        return modelService.listModels(token)
                .doOnSuccess(response -> log.info("Returning {} models.", response.data() == null ? 0 : response.data().size()));
    }

    @Operation(summary = "Retrieve model", description = "Retrieve metadata for a single model.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model metadata", content = @Content(schema = @Schema(implementation = ModelObject.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @GetMapping("/models/{model}")
    public Mono<ResponseEntity<ModelObject>> modelById(@PathVariable("model") String modelId, @RequestHeader HttpHeaders headers) {
        String token = readToken(headers);
        log.info("Received model lookup request. modelId={}", modelId);
        return modelService.getModel(token, modelId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(NOT_FOUND, "Model not found: " + modelId)));
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
