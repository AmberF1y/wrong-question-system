package com.wrongquestion.backend.rag;

import com.wrongquestion.backend.ai.AiProperties;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class RagWorker {
    private final RagService service;
    private final AiProperties ai;
    private final ExecutorService executor;
    private final Semaphore capacity;
    private final int concurrency;
    private volatile boolean ready;

    public RagWorker(RagService service, AiProperties ai, RagProperties properties) {
        this.service = service;
        this.ai = ai;
        concurrency = properties.concurrency();
        capacity = new Semaphore(concurrency);
        executor =
                Executors.newFixedThreadPool(
                        concurrency,
                        r -> {
                            var t = new Thread(r, "rag-worker");
                            t.setDaemon(true);
                            return t;
                        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (ai.workerEnabled()) {
            service.recoverInterrupted();
            ready = true;
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        if (!ready) return;
        for (int i = 0; i < concurrency && capacity.tryAcquire(); i++) {
            try {
                executor.execute(
                        () -> {
                            try {
                                service.executeNext();
                            } catch (RuntimeException e) {
                                org.slf4j.LoggerFactory.getLogger(getClass())
                                        .error("RAG dispatch failed; state retained for recovery");
                            } finally {
                                capacity.release();
                            }
                        });
            } catch (RejectedExecutionException e) {
                capacity.release();
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void recover() {
        if (ready) service.recoverExpired();
    }

    @PreDestroy
    public void close() {
        ready = false;
        executor.shutdownNow();
    }
}
