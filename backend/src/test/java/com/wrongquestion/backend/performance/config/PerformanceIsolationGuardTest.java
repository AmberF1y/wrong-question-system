package com.wrongquestion.backend.performance.config;

import com.wrongquestion.backend.question.image.config.QuestionImageStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceIsolationGuardTest {

    private static final String DATABASE =
            "wrong_question_system_perf_20260912";
    private static final String GIT_COMMIT =
            "72eaa0eafba3f760bfc8f05519250715cc20bed8";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void shouldAcceptMatchingDedicatedEnvironment() throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path images = temporaryDirectory
                .resolve("performance-data")
                .resolve(DATABASE)
                .resolve("question-images");
        PerformanceIsolationGuard guard = createGuard(
                DATABASE,
                images,
                repository,
                DATABASE
        );

        PerformanceIsolationGuard.VerifiedPerformanceEnvironment result =
                guard.verify();

        assertEquals(DATABASE, result.databaseName());
        assertEquals(
                images.toAbsolutePath().normalize().toString(),
                result.imageDirectory()
        );
    }

    @Test
    void shouldRejectProductionDatabase() throws Exception {
        PerformanceIsolationGuard guard = createGuard(
                "wrong_question_system",
                temporaryDirectory.resolve("images"),
                temporaryDirectory.resolve("repository"),
                "wrong_question_system"
        );

        assertThrows(IllegalStateException.class, guard::verify);
    }

    @Test
    void shouldRejectDatabaseMismatch() throws Exception {
        PerformanceIsolationGuard guard = createGuard(
                DATABASE,
                temporaryDirectory
                        .resolve("performance-data")
                        .resolve(DATABASE)
                        .resolve("question-images"),
                temporaryDirectory.resolve("repository"),
                "wrong_question_system_perf_20260913"
        );

        assertThrows(IllegalStateException.class, guard::verify);
    }

    @Test
    void shouldRejectImageDirectoryInsideRepository() throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        PerformanceIsolationGuard guard = createGuard(
                DATABASE,
                repository
                        .resolve(DATABASE)
                        .resolve("question-images"),
                repository,
                DATABASE
        );

        assertThrows(IllegalStateException.class, guard::verify);
    }

    @Test
    void shouldRejectImageDirectoryWithoutDatabasePathSegment()
            throws Exception {
        PerformanceIsolationGuard guard = createGuard(
                DATABASE,
                temporaryDirectory
                        .resolve("performance-data")
                        .resolve("question-images"),
                temporaryDirectory.resolve("repository"),
                DATABASE
        );

        assertThrows(IllegalStateException.class, guard::verify);
    }

    private PerformanceIsolationGuard createGuard(
            String expectedDatabase,
            Path imageDirectory,
            Path repository,
            String actualDatabase
    ) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()"))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn(actualDatabase);

        PerformanceSafetyProperties performanceProperties =
                new PerformanceSafetyProperties();
        performanceProperties.setExpectedDatabase(expectedDatabase);
        performanceProperties.setEnvironmentId("perf-small-20260912");
        performanceProperties.setGitCommit(GIT_COMMIT);
        performanceProperties.setRepositoryRoot(repository.toString());
        performanceProperties.setProductionImageDirectory(
                temporaryDirectory.resolve("production-images").toString()
        );
        performanceProperties.setProductionBackupDirectory(
                temporaryDirectory.resolve("production-backups").toString()
        );

        QuestionImageStorageProperties storageProperties =
                new QuestionImageStorageProperties();
        storageProperties.setQuestionImageDirectory(
                imageDirectory.toString()
        );

        return new PerformanceIsolationGuard(
                dataSource,
                performanceProperties,
                storageProperties
        );
    }
}
