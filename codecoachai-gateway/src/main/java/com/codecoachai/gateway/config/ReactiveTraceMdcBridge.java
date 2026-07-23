package com.codecoachai.gateway.config;

import java.util.function.Consumer;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

@Component
public class ReactiveTraceMdcBridge implements SmartLifecycle {

    public static final String TRACE_ID_CONTEXT_KEY = "traceId";

    private static final String HOOK_KEY =
            "com.codecoachai.gateway.config.ReactiveTraceMdcBridge";

    private volatile boolean running;

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        Hooks.onEachOperator(
                HOOK_KEY,
                Operators.lift((Scannable scannable, CoreSubscriber<? super Object> subscriber) ->
                        new TraceMdcCoreSubscriber<>(subscriber)));
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        Hooks.resetOnEachOperator(HOOK_KEY);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static final class TraceMdcCoreSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> delegate;

        private TraceMdcCoreSubscriber(CoreSubscriber<? super T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Context currentContext() {
            return delegate.currentContext();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            withTraceMdc(ignored -> delegate.onSubscribe(subscription));
        }

        @Override
        public void onNext(T value) {
            withTraceMdc(ignored -> delegate.onNext(value));
        }

        @Override
        public void onError(Throwable throwable) {
            withTraceMdc(ignored -> delegate.onError(throwable));
        }

        @Override
        public void onComplete() {
            withTraceMdc(ignored -> delegate.onComplete());
        }

        private void withTraceMdc(Consumer<Void> callback) {
            String previousTraceId = MDC.get(TRACE_ID_CONTEXT_KEY);
            String traceId = traceId(currentContext());
            try {
                setTraceId(traceId);
                callback.accept(null);
            } finally {
                setTraceId(previousTraceId);
            }
        }

        private static String traceId(ContextView contextView) {
            Object value = contextView.getOrDefault(TRACE_ID_CONTEXT_KEY, null);
            if (value == null) {
                return null;
            }
            String traceId = String.valueOf(value).trim();
            return StringUtils.hasText(traceId) ? traceId : null;
        }

        private static void setTraceId(String traceId) {
            if (StringUtils.hasText(traceId)) {
                MDC.put(TRACE_ID_CONTEXT_KEY, traceId);
            } else {
                MDC.remove(TRACE_ID_CONTEXT_KEY);
            }
        }
    }
}
