package com.gd.copilotapi.copilot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gd.copilotapi.config.CopilotCliProperties;
import com.gd.copilotapi.dto.openai.chat.ChatCompletionRequest;
import com.gd.copilotapi.dto.openai.chat.ChatMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class CopilotCliService {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliService.class);

    private final ObjectMapper objectMapper;
    private final CopilotCliProperties properties;

    public CopilotCliService(ObjectMapper objectMapper, CopilotCliProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Flux<String> streamAssistantDeltas(ChatCompletionRequest request, String githubToken) {
        log.debug("Starting CLI streaming completion. messageCount={}, requestedModel={}", messageCount(request), request.model());
        return runCopilot(request, githubToken, true)
                .handle((event, sink) -> {
                    if (isAuthenticationFailure(event.rawLine())) {
                        log.warn("Detected authentication failure while streaming CLI output.");
                        sink.error(new CopilotAuthenticationException(
                                "GitHub token authentication failed. Ensure your token is valid, not expired, and has Copilot Requests permission."
                        ));
                        return;
                    }

                    if (!"assistant.message_delta".equals(event.type())) {
                        return;
                    }

                    String delta = event.data().path("deltaContent").asText("");
                    if (StringUtils.hasText(delta)) {
                        sink.next(delta);
                    }
                });
    }

    public Mono<String> complete(ChatCompletionRequest request, String githubToken) {
        log.debug("Starting CLI non-stream completion. messageCount={}, requestedModel={}", messageCount(request), request.model());
        return runCopilot(request, githubToken, false)
                .collectList()
                .map(this::extractAssistantMessage);
    }

    public Mono<List<String>> listAvailableModels(String githubToken) {
        log.debug("Loading available models from Copilot CLI.");
        return Mono.fromCallable(() -> loadAvailableModels(githubToken))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public String resolveModel(String requestedModel) {
        return StringUtils.hasText(requestedModel) ? requestedModel : properties.getDefaultModel();
    }

    List<String> loadAvailableModels(String githubToken) {
        List<String> command = List.of(properties.getCommand(), properties.getSubcommand(), "--", "help", "config");
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        log.debug("Executing model catalog command: {}", String.join(" ", command));
        if (StringUtils.hasText(githubToken)) {
            processBuilder.environment().put("GH_TOKEN", githubToken);
            processBuilder.environment().put("GITHUB_TOKEN", githubToken);
        }

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (isAuthenticationFailure(output.toString().lines().toList())) {
                    log.warn("Model catalog command failed due to authentication error.");
                    throw new CopilotAuthenticationException(
                            "GitHub token authentication failed. Ensure your token is valid, not expired, and has Copilot Requests permission."
                    );
                }
                log.warn("Model catalog command failed with exitCode={}", exitCode);
                throw new IllegalStateException("Failed to load available Copilot models. Exit code " + exitCode + ".");
            }

            List<String> models = parseAvailableModels(output.toString());
            log.info("Loaded {} available models from Copilot CLI.", models.size());
            return models;
        } catch (IOException exception) {
            log.error("Failed to execute model catalog command.", exception);
            throw new IllegalStateException("Failed to load available Copilot models.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while loading available models.", exception);
            throw new IllegalStateException("Interrupted while loading available Copilot models.", exception);
        }
    }

    List<String> parseAvailableModels(String helpOutput) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add("auto");

        boolean inModelSection = false;
        for (String line : helpOutput.split("\\R")) {
            if (line.matches("^\\s*`model`:\\s*.*")) {
                inModelSection = true;
                continue;
            }

            if (inModelSection && line.matches("^\\s*`[A-Za-z][^`]*`:\\s*.*")) {
                break;
            }

            if (inModelSection) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*-\\s*\"([^\"]+)\"\\s*$").matcher(line);
                if (matcher.matches()) {
                    models.add(matcher.group(1));
                }
            }
        }

        if (models.size() == 1) {
            throw new IllegalStateException("No Copilot models were discovered from the CLI help output.");
        }

        return List.copyOf(models);
    }

    String renderPrompt(ChatCompletionRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are responding through an OpenAI-compatible chat completions API.\n");
        builder.append("Return only the assistant's next reply content.\n");
        builder.append("Use plain text by default and avoid markdown formatting (for example: **bold**, headers, lists) unless explicitly requested.\n");
        builder.append("Do not add protocol framing, markdown fences, or JSON unless the conversation explicitly asks for them.\n\n");

        for (ChatMessage message : normalizedMessages(request)) {
            String role = normalizeRole(message.role());
            builder.append('<').append(role).append('>').append('\n');
            String content = stringifyMessageContent(message.content());
            if (StringUtils.hasText(content)) {
                builder.append(content.trim());
            }
            builder.append('\n');
            builder.append("</").append(role).append(">\n\n");
        }

        builder.append("Produce the assistant reply for the conversation above.");
        return builder.toString();
    }

    List<ChatMessage> normalizedMessages(ChatCompletionRequest request) {
        if (request.messages() != null && !request.messages().isEmpty()) {
            return List.copyOf(request.messages());
        }

        throw new IllegalArgumentException("Provide at least one chat message in messages.");
    }

    String extractAssistantMessage(List<CliEvent> events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            CliEvent event = events.get(index);
            String rawLine = event.rawLine();
            if (isAuthenticationFailure(rawLine)) {
                log.warn("Detected authentication failure while extracting assistant message.");
                throw new CopilotAuthenticationException("GitHub token authentication failed. Ensure your token is valid, not expired, and has Copilot Requests permission.");
            }
        }

        for (int index = events.size() - 1; index >= 0; index--) {
            CliEvent event = events.get(index);
            if ("assistant.message".equals(event.type())) {
                String content = event.data().path("content").asText("");
                if (StringUtils.hasText(content)) {
                    log.debug("Extracted assistant message from final assistant.message event. contentLength={}", content.length());
                    return content;
                }
            }
        }

        for (int index = events.size() - 1; index >= 0; index--) {
            CliEvent event = events.get(index);
            String content = extractContentFromRawLine(event.rawLine());
            if (StringUtils.hasText(content)) {
                log.debug("Extracted assistant message from raw CLI line fallback. contentLength={}", content.length());
                return content;
            }
        }

        StringBuilder builder = new StringBuilder();
        for (CliEvent event : events) {
            if (!"assistant.message_delta".equals(event.type())) {
                continue;
            }

            String delta = event.data().path("deltaContent").asText("");
            if (StringUtils.hasText(delta)) {
                builder.append(delta);
            }
        }

        if (builder.length() == 0) {
            String lastRawLine = events.isEmpty() ? "<no output>" : events.get(events.size() - 1).rawLine();
            log.warn("No assistant response extracted from CLI events. lastLine={}", summarizeLine(lastRawLine));
            throw new IllegalStateException("Copilot CLI returned no assistant response. Last output line: " + lastRawLine);
        }

        log.debug("Extracted assistant message by combining deltas. contentLength={}", builder.length());
        return builder.toString();
    }

    private String extractContentFromRawLine(String rawLine) {
        if (!StringUtils.hasText(rawLine)) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(rawLine);
            String content = root.path("data").path("content").asText("");
            if (StringUtils.hasText(content)) {
                return content;
            }

            String deltaContent = root.path("data").path("deltaContent").asText("");
            if (StringUtils.hasText(deltaContent)) {
                return deltaContent;
            }
        } catch (IOException exception) {
            return "";
        }

        return "";
    }

    private Flux<CliEvent> runCopilot(ChatCompletionRequest request, String githubToken, boolean stream) {
        return Flux.create(sink -> {
            AtomicBoolean terminated = new AtomicBoolean();
            Process process;
            List<String> command = buildCommand(request, stream);
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(command)
                        .redirectErrorStream(true);
                processBuilder.environment().put("GH_TOKEN", githubToken);
                processBuilder.environment().put("GITHUB_TOKEN", githubToken);
                process = processBuilder.start();
                log.debug("Started Copilot CLI process. stream={}, model={}, command={}", stream, resolveModel(request.model()), String.join(" ", command));
            } catch (IOException exception) {
                log.error("Failed to start Copilot CLI process.", exception);
                sink.error(new IllegalStateException("Failed to start the Copilot CLI process.", exception));
                return;
            }

            sink.onDispose(process::destroyForcibly);

            String prompt = renderPrompt(request);
            Thread writerThread = Thread.ofVirtual().start(() -> writePrompt(process, prompt, sink, terminated));

            Thread.ofVirtual().start(() -> {
                List<String> rawLines = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }

                        rawLines.add(line);
                        sink.next(parseEvent(line));
                    }

                    writerThread.join();

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        if (isAuthenticationFailure(rawLines)) {
                            log.warn("Copilot CLI exited with authentication failure. stream={}", stream);
                            signalError(sink, terminated, new CopilotAuthenticationException(
                                    "GitHub token authentication failed. Ensure your token is valid, not expired, and has Copilot Requests permission."
                            ));
                            return;
                        }

                        log.warn("Copilot CLI exited with code {}. stream={}, lastLine={}", exitCode, stream,
                                rawLines.isEmpty() ? "<no output>" : summarizeLine(rawLines.get(rawLines.size() - 1)));
                        signalError(sink, terminated, new IllegalStateException(buildFailureMessage(exitCode, rawLines)));
                        return;
                    }

                    log.debug("Copilot CLI process completed successfully. stream={}", stream);
                    signalComplete(sink, terminated);
                } catch (Exception exception) {
                    log.error("Failed while reading Copilot CLI output.", exception);
                    signalError(sink, terminated, new IllegalStateException("Failed to read Copilot CLI output.", exception));
                }
            });
        });
    }

    List<String> buildCommand(ChatCompletionRequest request, boolean stream) {
        List<String> command = new ArrayList<>();
        command.add(properties.getCommand());
        command.add(properties.getSubcommand());
        command.add("--");
        command.add("--output-format");
        command.add("json");
        command.add("--stream");
        command.add(stream ? "on" : "off");
        command.add("-s");

        if (properties.isNoCustomInstructions()) {
            command.add("--no-custom-instructions");
        }
        if (properties.isDisableBuiltinMcps()) {
            command.add("--disable-builtin-mcps");
        }
        if (properties.isNoAskUser()) {
            command.add("--no-ask-user");
        }

        String model = resolveModel(request.model());
        if (StringUtils.hasText(model)) {
            command.add("--model");
            command.add(model);
        }

        return command;
    }

    private void writePrompt(Process process, String prompt, reactor.core.publisher.FluxSink<CliEvent> sink, AtomicBoolean terminated) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(prompt);
            writer.flush();
            log.debug("Prompt sent to Copilot CLI. promptLengthChars={}", prompt.length());
        } catch (IOException exception) {
            process.destroyForcibly();
            log.error("Failed to write prompt to Copilot CLI process.", exception);
            signalError(sink, terminated, new IllegalStateException("Failed to write the prompt to the Copilot CLI process.", exception));
        }
    }

    private CliEvent parseEvent(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            return new CliEvent(root.path("type").asText("unknown"), root.path("data"), line);
        } catch (IOException exception) {
            JsonNode rawNode = objectMapper.createObjectNode().put("line", line);
            return new CliEvent("raw", rawNode, line);
        }
    }

    private String buildFailureMessage(int exitCode, List<String> rawLines) {
        String detail = rawLines.stream()
                .reduce((first, second) -> second)
                .orElse("The Copilot CLI exited without output.");
        return "Copilot CLI exited with code " + exitCode + ": " + detail;
    }

    private int messageCount(ChatCompletionRequest request) {
        return request.messages() == null ? 0 : request.messages().size();
    }

    private String summarizeLine(String rawLine) {
        if (!StringUtils.hasText(rawLine)) {
            return "<empty>";
        }
        if (rawLine.length() <= 220) {
            return rawLine;
        }
        return rawLine.substring(0, 220) + "...";
    }

    boolean isAuthenticationFailure(List<String> rawLines) {
        for (String rawLine : rawLines) {
            if (isAuthenticationFailure(rawLine)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAuthenticationFailure(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }

        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("authentication failed")
                || lower.contains("token may be invalid")
                || lower.contains("copilot requests")
                || lower.contains("gh auth login")
                || lower.contains("gh auth status")
                || lower.contains("authenticate with the github cli")
                || lower.contains("verify the token is valid");
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }

        return switch (role.toLowerCase(Locale.ROOT)) {
            case "system", "assistant", "user", "tool", "developer" -> role.toLowerCase(Locale.ROOT);
            default -> "user";
        };
    }

    private String stringifyMessageContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            return String.valueOf(content);
        }
    }

    private void signalComplete(reactor.core.publisher.FluxSink<CliEvent> sink, AtomicBoolean terminated) {
        if (terminated.compareAndSet(false, true)) {
            sink.complete();
        }
    }

    private void signalError(reactor.core.publisher.FluxSink<CliEvent> sink, AtomicBoolean terminated, IllegalStateException exception) {
        if (terminated.compareAndSet(false, true)) {
            sink.error(exception);
        }
    }

    record CliEvent(String type, JsonNode data, String rawLine) {
    }
}
