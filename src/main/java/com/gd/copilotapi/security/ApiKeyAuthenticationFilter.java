package com.gd.copilotapi.security;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class ApiKeyAuthenticationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String method = exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name();
        if (!path.startsWith("/v1")) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest().getHeaders());
        if (!StringUtils.hasText(token)) {
            log.warn("Authentication failed: missing API key. method={}, path={}", method, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"error\":{\"message\":\"Missing API key\",\"type\":\"authentication_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }

        log.debug("Authentication passed. method={}, path={}", method, path);
        exchange.getAttributes().put("githubToken", token);
        return chain.filter(exchange);
    }

    private String resolveToken(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        String token = headers.getFirst("X-GitHub-Token");
        return token == null ? "" : token.trim();
    }
}
