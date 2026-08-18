# copilot-cli-wrapper-api

An OpenAI-compatible HTTP wrapper around the GitHub Copilot CLI.

The service exposes:

- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/chat/completions/stream`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`

Streaming requests return Server-Sent Events in the same `data: ...` format used by the OpenAI Chat Completions API, which makes the wrapper usable from Spring AI's OpenAI client.

`GET /v1/models` is backed by the installed Copilot CLI's live model catalog, so the list tracks the CLI version on the machine running the wrapper.

## Requirements

- Java 21+
- Maven 3.9+
- GitHub CLI with Copilot access
- An authenticated `gh` session that can run `gh copilot`

Quick sanity check:

```bash
gh copilot -- -p "Reply with exactly: hello" --output-format json --stream on -s --no-custom-instructions --disable-builtin-mcps --no-ask-user
```

## Run

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080` by default.

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.
The raw OpenAPI document is available at `http://localhost:8080/v3/api-docs`.

## Configuration

Environment variables:

- `PORT`: HTTP port, default `8080`
- `COPILOT_CLI_COMMAND`: executable name, default `gh`
- `COPILOT_CLI_SUBCOMMAND`: subcommand name, default `copilot`
- `COPILOT_MODEL`: default model passed to the Copilot CLI, default `auto`
- `COPILOT_DISABLE_BUILTIN_MCPS`: default `true`
- `COPILOT_NO_CUSTOM_INSTRUCTIONS`: default `true`
- `COPILOT_NO_ASK_USER`: default `true`

Authentication:

- Every `/v1/*` request must include a GitHub PAT.
- Supported headers: `Authorization: Bearer <token>` or `X-GitHub-Token: <token>`.
- The wrapper injects that token into the spawned GitHub CLI process via `GH_TOKEN` and `GITHUB_TOKEN`.

This means the Spring AI `api-key` value should be a real GitHub PAT, not a wrapper-local secret.

Long prompts are not sent as command-line arguments. The wrapper streams the rendered prompt to the Copilot CLI over stdin, which avoids Windows command-length limits for larger requests.

## Example Requests

Non-streaming:

```bash
curl http://localhost:8080/v1/chat/completions \
	-H 'Authorization: Bearer <github-pat>' \
	-H 'Content-Type: application/json' \
	-d '{
		"model": "auto",
		"messages": [
			{"role": "system", "content": "Be concise."},
			{"role": "user", "content": "Explain what Java virtual threads are in two sentences."}
		]
	}'
```

Streaming:

```bash
curl http://localhost:8080/v1/chat/completions/stream \
	-H 'Authorization: Bearer <github-pat>' \
	-H 'Content-Type: application/json' \
	-N \
	-d '{
		"model": "auto",
		"messages": [
			{"role": "system", "content": "Write in haiku form."},
			{"role": "user", "content": "Write a haiku about SSE streams."}
		]
	}'
```

## Spring AI Setup

Point Spring AI's OpenAI client at the wrapper base URL:

```yaml
spring:
	ai:
		openai:
			api-key: ${GITHUB_PAT}
			base-url: http://localhost:8080
			chat:
				options:
					model: auto
```

## Notes

- The wrapper accepts an OpenAI-style `messages` array.
- Returned `usage` token counts are placeholders because the Copilot CLI JSON stream does not expose OpenAI token totals.
- The wrapper intentionally disables custom instructions, built-in MCP servers, and interactive user prompts by default to keep HTTP calls predictable.