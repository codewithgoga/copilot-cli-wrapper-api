package com.gd.copilotapi.logging;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

@Configuration
public class ReactorMdcHookConfiguration {

    private static final String HOOK_KEY = "copilotapiMdcHook";

    @PostConstruct
    void registerMdcHook() {
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((scannable, subscriber) -> new ReactorMdcLifter<>(subscriber)));
    }

    @PreDestroy
    void cleanupMdcHook() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }
}