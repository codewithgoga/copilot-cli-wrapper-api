package com.gd.copilotapi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.gd.copilotapi.copilot.CopilotClient;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = CopilotApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenAiContractIntegrationTest {

        private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

        @LocalServerPort
        private int port;

        private WebTestClient webTestClient;

        @MockitoBean
    private CopilotClient copilotClient;

    @BeforeEach
    void setup() {
                webTestClient = WebTestClient.bindToServer()
                                .baseUrl("http://localhost:" + port)
                                .build();

        when(copilotClient.listModels(anyString())).thenReturn(Mono.just(List.of("auto", "gpt-5")));
        when(copilotClient.resolveModel(any())).thenReturn("auto");
        when(copilotClient.complete(any(ChatCompletionRequest.class), anyString())).thenReturn(Mono.just("hello from copilot"));
        when(copilotClient.stream(any(ChatCompletionRequest.class), anyString())).thenReturn(Flux.just("hello", " world"));
    }

    @Test
    void modelsEndpoints_areCompatible() {
        webTestClient.get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("auto");

        webTestClient.get()
                .uri("/v1/models/gpt-5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("gpt-5")
                .jsonPath("$.object").isEqualTo("model");
    }

    @Test
    void chatAndResponsesEndpoints_areCompatible() {
        String chatBody = """
                {
                  "model": "auto",
                  "messages": [{"role": "user", "content": "hello"}],
                  "stream": false
                }
                """;

        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .bodyValue(chatBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("chat.completion")
                .jsonPath("$.choices[0].message.content").isEqualTo("hello from copilot");

        String streamingBody = """
                {
                  "model": "auto",
                  "messages": [{"role": "user", "content": "stream"}],
                  "stream": true
                }
                """;

        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .bodyValue(streamingBody)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        webTestClient.post()
                .uri("/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .bodyValue(streamingBody)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        String responseBody = """
                {
                  "model": "auto",
                  "input": "hello",
                  "instructions": "be concise"
                }
                """;

        webTestClient.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .bodyValue(responseBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response")
                .jsonPath("$.output[0].content[0].text").isEqualTo("hello from copilot");
    }

    @Test
    void embeddingModerationImageAndAudioEndpoints_areCompatible() {
        webTestClient.post()
                .uri("/v1/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"text-embedding-3-small\",\"input\":\"hello\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].object").isEqualTo("embedding");

        webTestClient.post()
                .uri("/v1/moderations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"omni-moderation-latest\",\"input\":\"hello\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.results[0].flagged").isEqualTo(false);

        webTestClient.post()
                .uri("/v1/images/generations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"gpt-image-1\",\"prompt\":\"mountain\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].b64_json").exists();

        MultiValueMap<String, Object> transcriptionForm = new LinkedMultiValueMap<>();
        transcriptionForm.add("file", namedResource("audio.wav", "audio".getBytes(StandardCharsets.UTF_8)));
        transcriptionForm.add("model", "whisper-1");

        webTestClient.post()
                .uri("/v1/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(transcriptionForm)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.text").exists();

        webTestClient.post()
                .uri("/v1/audio/speech")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"tts-1\",\"input\":\"hello\",\"voice\":\"alloy\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filesAndBatchesEndpoints_areCompatible() {
        MultiValueMap<String, Object> fileForm = new LinkedMultiValueMap<>();
        fileForm.add("file", namedResource("hello.txt", "content".getBytes(StandardCharsets.UTF_8)));
        fileForm.add("purpose", "batch");

        Map<String, Object> uploadResponse = webTestClient.post()
                .uri("/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(fileForm)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        if (uploadResponse == null) {
            throw new IllegalStateException("Expected upload response body.");
        }

        String fileId = String.valueOf(uploadResponse.get("id"));

        webTestClient.get()
                .uri("/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").exists();

        webTestClient.get()
                .uri("/v1/files/{file_id}", fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(fileId);

        webTestClient.delete()
                .uri("/v1/files/{file_id}", fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("deleted");

        Map<String, Object> batchResponse = webTestClient.post()
                .uri("/v1/batches")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"input_file_id\":\"file-1\",\"endpoint\":\"/v1/chat/completions\",\"completion_window\":\"24h\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        if (batchResponse == null) {
            throw new IllegalStateException("Expected batch response body.");
        }

        String batchId = String.valueOf(batchResponse.get("id"));

        webTestClient.get()
                .uri("/v1/batches")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").exists();

        webTestClient.get()
                .uri("/v1/batches/{batch_id}", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(batchId);

        webTestClient.post()
                .uri("/v1/batches/{batch_id}/cancel", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("cancelled");
    }

    @Test
    void apiKey_isRequired() {
        webTestClient.get()
                .uri("/v1/models")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void correlationId_isGeneratedAndPropagated() {
        webTestClient.get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(CORRELATION_ID_HEADER);

        String providedCorrelationId = "test-cid-12345";
        webTestClient.get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .header(CORRELATION_ID_HEADER, providedCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CORRELATION_ID_HEADER, providedCorrelationId);
    }

    private ByteArrayResource namedResource(String filename, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
