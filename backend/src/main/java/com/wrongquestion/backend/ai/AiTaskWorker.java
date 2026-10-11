package com.wrongquestion.backend.ai;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class AiTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AiTaskWorker.class);
    private final AiTaskService service;
    private final AiProperties properties;
    private final ExecutorService executor;
    private final Semaphore capacity;
    private volatile boolean ready;

    public AiTaskWorker(AiTaskService service, AiProperties properties) {
        this.service = service;
        this.properties = properties;
        capacity = new Semaphore(properties.concurrency());
        executor =
                Executors.newFixedThreadPool(
                        properties.concurrency(),
                        r -> {
                            Thread thread = new Thread(r, "ai-image-worker");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.workerEnabled()) {
            service.recoverInterrupted();
            ready = true;
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        if (!ready) return;
        for (int i = 0; i < properties.concurrency() && capacity.tryAcquire(); i++) {
            try {
                executor.execute(
                        () -> {
                            try {
                                service.executeNext();
                            } catch (RuntimeException e) {
                                LOG.error(
                                        "AI task dispatch failed; task state retained for recovery",
                                        e);
                            } finally {
                                capacity.release();
                            }
                        });
            } catch (RejectedExecutionException e) {
                capacity.release();
            }
        }
    }

    @PreDestroy
    public void close() {
        ready = false;
        executor.shutdownNow();
    }

    @Scheduled(fixedDelay = 15000)
    public void recoverExpired() {
        if (ready) {
            try {
                service.recoverExpired();
            } catch (RuntimeException e) {
                LOG.error("AI task recovery check failed", e);
            }
        }
    }
}
