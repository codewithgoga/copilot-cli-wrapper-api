package com.gd.copilotapi.copilot;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gd.copilotapi.config.CopilotCliProperties;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;
import com.gd.copilotapi.dto.openai.chat.ChatMessage;

class CopilotCliServiceTest {

    private final CopilotCliService service = new CopilotCliService(new ObjectMapper(), new CopilotCliProperties());

    @Test
    void renderPromptPreservesConversationRoles() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                "auto",
                List.of(
                        new ChatMessage("system", "Be concise.", null, null, null),
                        new ChatMessage("user", "Summarize SSE.", null, null, null),
                        new ChatMessage("assistant", "Server-Sent Events stream data.", null, null, null),
                        new ChatMessage("user", "Add one more detail.", null, null, null)
                ),
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        String prompt = service.renderPrompt(request);

        assertThat(prompt)
                .contains("<system>\nBe concise.\n</system>")
                .contains("<user>\nSummarize SSE.\n</user>")
                .contains("<assistant>\nServer-Sent Events stream data.\n</assistant>")
                .contains("Produce the assistant reply for the conversation above.");
    }

    @Test
    void extractAssistantMessageFallsBackToDeltas() {
        var deltaOne = new CopilotCliService.CliEvent(
                "assistant.message_delta",
                new ObjectMapper().createObjectNode().put("deltaContent", "hel"),
                ""
        );
        var deltaTwo = new CopilotCliService.CliEvent(
                "assistant.message_delta",
                new ObjectMapper().createObjectNode().put("deltaContent", "lo"),
                ""
        );

        assertThat(service.extractAssistantMessage(List.of(deltaOne, deltaTwo))).isEqualTo("hello");
    }

    @Test
    void buildCommandDoesNotPutPromptOnCommandLine() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                "auto",
                List.of(new ChatMessage("user", "A very long prompt should go over stdin.", null, null, null)),
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<String> command = service.buildCommand(request, true);

        assertThat(command)
                .doesNotContain("-p")
                .doesNotContain(service.renderPrompt(request))
                .contains("--output-format", "json", "--stream", "on", "-s");
    }

    @Test
    void parseAvailableModelsExtractsModelCatalog() {
        String helpOutput = """
                Configuration Settings:

                  `model`: AI model to use for Copilot CLI; can be changed with /model command or --model flag option.
                    - "claude-sonnet-5"
                    - "claude-sonnet-4.6"
                    - "gpt-5.4"
                    - "gpt-5.4-mini"
                    - "gpt-5-mini"

                  `contextTier`: context window tier for tiered-pricing models.
                """;

        assertThat(service.parseAvailableModels(helpOutput))
                .containsExactly(
                        "auto",
                        "claude-sonnet-5",
                        "claude-sonnet-4.6",
                        "gpt-5.4",
                        "gpt-5.4-mini",
                        "gpt-5-mini"
                );
    }

    @Test
    void extractAssistantMessageThrowsAuthenticationErrorWhenCliReportsAuthFailure() {
        var authErrorEvent = new CopilotCliService.CliEvent(
                "raw",
                new ObjectMapper().createObjectNode().put("line", "Error: Authentication failed"),
                "Error: Authentication failed"
        );

        assertThatThrownBy(() -> service.extractAssistantMessage(List.of(authErrorEvent)))
                .isInstanceOf(CopilotAuthenticationException.class)
                .hasMessageContaining("authentication failed");
    }
}
