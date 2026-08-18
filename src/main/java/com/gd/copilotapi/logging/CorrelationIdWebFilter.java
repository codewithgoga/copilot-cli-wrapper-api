package com.gd.copilotapi.logging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-200)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders());
        long startNanos = System.nanoTime();

        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        log.info("Request started. correlationId={}, method={}, path={}", correlationId, method, path);

        return chain.filter(exchange)
                .contextWrite(context -> context.put(CORRELATION_ID_CONTEXT_KEY, correlationId))
                .doFinally(signalType -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode == null ? 200 : statusCode.value();
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                        log.info("Request finished. correlationId={}, method={}, path={}, status={}, signal={}, durationMs={}",
                            correlationId, method, path, status, signalType.name(), durationMs);
                });
    }

    private String resolveCorrelationId(HttpHeaders headers) {
        String incoming = headers.getFirst(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}