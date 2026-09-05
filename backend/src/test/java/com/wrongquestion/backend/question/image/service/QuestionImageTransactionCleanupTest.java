package com.wrongquestion.backend.question.image.service;

import com.wrongquestion.backend.question.image.exception.QuestionImageStorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionImageTransactionCleanupTest {

    private static final String NEW_PATH =
            "questions/10/00000000-0000-0000-0000-000000000010.png";
    private static final String OLD_PATH =
            "questions/10/00000000-0000-0000-0000-000000000009.jpg";

    @Mock
    private LocalQuestionImageStorage storage;

    private QuestionImageTransactionCleanup cleanup;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup = new QuestionImageTransactionCleanup(storage);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldDeleteOldImageOnlyAfterReplacementCommit() {
        cleanup.registerReplacement(NEW_PATH, OLD_PATH);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(item -> item.afterCompletion(
                TransactionSynchronization.STATUS_COMMITTED
        ));

        verify(storage).delete(OLD_PATH);
        verify(storage, never()).delete(NEW_PATH);
    }

    @Test
    void shouldDeleteNewImageOnlyAfterReplacementRollback() {
        cleanup.registerReplacement(NEW_PATH, OLD_PATH);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(item -> item.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                ));

        verify(storage).delete(NEW_PATH);
        verify(storage, never()).delete(OLD_PATH);
    }

    @Test
    void shouldDeleteDetachedImageOnlyAfterCommit() {
        cleanup.registerDeleteAfterCommit(OLD_PATH);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(item -> item.afterCompletion(
                TransactionSynchronization.STATUS_COMMITTED
        ));

        verify(storage).delete(OLD_PATH);
    }

    @Test
    void shouldDeleteUncoordinatedNewFileAndRejectRegistration() {
        TransactionSynchronizationManager.clearSynchronization();

        assertThrows(
                QuestionImageStorageException.class,
                () -> cleanup.registerReplacement(NEW_PATH, OLD_PATH)
        );

        verify(storage).delete(NEW_PATH);
        verify(storage, never()).delete(OLD_PATH);
    }
}
