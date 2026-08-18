package com.gd.copilotapi.dto.openai.chat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        @JsonProperty("top_p")
        Double topP,
        Integer n,
        Boolean stream,
        @JsonProperty("stream_options")
        Map<String, Object> streamOptions,
        @JsonProperty("max_tokens")
        Integer maxTokens,
        @JsonProperty("max_completion_tokens")
        Integer maxCompletionTokens,
        @JsonProperty("presence_penalty")
        Double presencePenalty,
        @JsonProperty("frequency_penalty")
        Double frequencyPenalty,
        @JsonProperty("logit_bias")
        Map<String, Object> logitBias,
        List<String> stop,
        String user,
        List<ChatTool> tools,
        @JsonProperty("tool_choice")
        Object toolChoice,
        @JsonProperty("parallel_tool_calls")
        Boolean parallelToolCalls,
        @JsonProperty("response_format")
        ResponseFormat responseFormat,
        Map<String, Object> metadata
) {
}
