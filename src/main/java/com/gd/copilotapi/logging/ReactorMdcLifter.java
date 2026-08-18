package com.gd.copilotapi.logging;

import java.util.Map;

import org.reactivestreams.Subscription;
import org.slf4j.MDC;

import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

class ReactorMdcLifter<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<? super T> delegate;

    ReactorMdcLifter(CoreSubscriber<? super T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        withMdc(() -> delegate.onSubscribe(subscription));
    }

    @Override
    public void onNext(T value) {
        withMdc(() -> delegate.onNext(value));
    }

    @Override
    public void onError(Throwable throwable) {
        withMdc(() -> delegate.onError(throwable));
    }

    @Override
    public void onComplete() {
        withMdc(delegate::onComplete);
    }

    @Override
    public Context currentContext() {
        return delegate.currentContext();
    }

    private void withMdc(Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            Context context = currentContext();
            if (context.hasKey(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY)) {
                Object correlationValue = context.get(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY);
                String correlationId = correlationValue == null ? "" : correlationValue.toString();
                MDC.put(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, correlationId);
            } else {
                MDC.remove(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY);
            }
            action.run();
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}