package com.wrongquestion.backend.question.image.service;

import com.wrongquestion.backend.question.image.exception.QuestionImageStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class QuestionImageTransactionCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            QuestionImageTransactionCleanup.class
    );

    private final LocalQuestionImageStorage storage;

    public QuestionImageTransactionCleanup(LocalQuestionImageStorage storage) {
        this.storage = storage;
    }

    public void registerReplacement(
            String newRelativePath,
            String oldRelativePath
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(newRelativePath, "uncoordinated upload");
            throw new QuestionImageStorageException("题目图片事务协调失败");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(oldRelativePath, "replaced image");
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteQuietly(
                                    newRelativePath,
                                    "rolled-back upload"
                            );
                        }
                    }
                }
        );
    }

    public void registerDeleteAfterCommit(String relativePath) {
        if (relativePath == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new QuestionImageStorageException("题目图片事务协调失败");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(relativePath, "detached image");
                    }
                }
        );
    }

    private void deleteQuietly(String relativePath, String reason) {
        if (relativePath == null) {
            return;
        }
        try {
            storage.delete(relativePath);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to clean up {} at storage key {}",
                    reason,
                    relativePath,
                    exception
            );
        }
    }
}
