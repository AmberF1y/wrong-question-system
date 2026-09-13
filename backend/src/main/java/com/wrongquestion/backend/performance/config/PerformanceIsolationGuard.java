package com.wrongquestion.backend.performance.config;

import com.wrongquestion.backend.question.image.config.QuestionImageStorageProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

@Profile("performance")
@Component
public class PerformanceIsolationGuard implements ApplicationRunner {

    private static final Pattern DATABASE_PATTERN = Pattern.compile(
            "^wrong_question_system_perf_[0-9]{8}(?:_[a-z0-9_]+)?$"
    );
    private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile(
            "^perf-[a-z0-9][a-z0-9-]{2,63}$"
    );
    private static final Pattern GIT_COMMIT_PATTERN = Pattern.compile(
            "^[0-9a-f]{40}$"
    );

    private final DataSource dataSource;
    private final PerformanceSafetyProperties performanceProperties;
    private final QuestionImageStorageProperties storageProperties;

    private volatile VerifiedPerformanceEnvironment verifiedEnvironment;

    public PerformanceIsolationGuard(
            DataSource dataSource,
            PerformanceSafetyProperties performanceProperties,
            QuestionImageStorageProperties storageProperties
    ) {
        this.dataSource = dataSource;
        this.performanceProperties = performanceProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        verifiedEnvironment = verify();
    }

    public VerifiedPerformanceEnvironment getVerifiedEnvironment() {
        VerifiedPerformanceEnvironment current = verifiedEnvironment;
        if (current == null) {
            throw new IllegalStateException(
                    "性能测试隔离环境尚未完成启动校验"
            );
        }
        return current;
    }

    VerifiedPerformanceEnvironment verify() {
        String expectedDatabase = performanceProperties
                .getExpectedDatabase();
        requireMatch(
                DATABASE_PATTERN,
                expectedDatabase,
                "性能测试数据库名称不符合安全规则"
        );
        if (
                "wrong_question_system".equalsIgnoreCase(expectedDatabase)
                        || "wrong_question_system_test".equalsIgnoreCase(
                                expectedDatabase
                        )
        ) {
            throw new IllegalStateException("禁止使用正式库或普通测试库");
        }

        String actualDatabase = readCurrentDatabase();
        if (!expectedDatabase.equals(actualDatabase)) {
            throw new IllegalStateException(
                    "实际数据库与批准的性能测试数据库不一致"
            );
        }

        String environmentId = performanceProperties.getEnvironmentId();
        requireMatch(
                ENVIRONMENT_PATTERN,
                environmentId,
                "性能测试环境标识不符合安全规则"
        );
        String gitCommit = performanceProperties.getGitCommit();
        requireMatch(
                GIT_COMMIT_PATTERN,
                gitCommit,
                "Git提交标识不符合安全规则"
        );

        Path repositoryRoot = normalizePath(
                performanceProperties.getRepositoryRoot(),
                "仓库根目录"
        );
        Path productionImageDirectory = normalizePath(
                performanceProperties.getProductionImageDirectory(),
                "正式图片目录"
        );
        Path productionBackupDirectory = normalizePath(
                performanceProperties.getProductionBackupDirectory(),
                "正式备份目录"
        );
        Path imageDirectory = normalizePath(
                storageProperties.getQuestionImageDirectory(),
                "性能测试图片目录"
        );

        rejectInside(
                imageDirectory,
                repositoryRoot,
                "性能测试图片目录不能位于仓库内"
        );
        rejectInside(
                imageDirectory,
                productionImageDirectory,
                "性能测试图片目录不能位于正式图片目录内"
        );
        rejectInside(
                imageDirectory,
                productionBackupDirectory,
                "性能测试图片目录不能位于正式备份目录内"
        );

        boolean containsDatabaseSegment = StreamSupport.stream(
                        imageDirectory.spliterator(),
                        false
                )
                .map(Path::toString)
                .anyMatch(segment -> segment.equalsIgnoreCase(
                        expectedDatabase
                ));
        if (!containsDatabaseSegment) {
            throw new IllegalStateException(
                    "性能测试图片目录必须包含专用数据库名路径段"
            );
        }

        return new VerifiedPerformanceEnvironment(
                environmentId,
                actualDatabase,
                imageDirectory.toString(),
                gitCommit
        );
    }

    private String readCurrentDatabase() {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT DATABASE()"
                )
        ) {
            if (!resultSet.next()) {
                throw new IllegalStateException("无法读取当前数据库名称");
            }
            String databaseName = resultSet.getString(1);
            if (databaseName == null || databaseName.isBlank()) {
                throw new IllegalStateException("当前连接没有选择数据库");
            }
            return databaseName;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "性能测试数据库身份校验失败",
                    exception
            );
        }
    }

    private void requireMatch(
            Pattern pattern,
            String value,
            String message
    ) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalStateException(message);
        }
    }

    private Path normalizePath(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不能为空");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private void rejectInside(
            Path candidate,
            Path forbiddenRoot,
            String message
    ) {
        String candidateValue = candidate.toString()
                .toLowerCase(Locale.ROOT);
        String forbiddenValue = forbiddenRoot.toString()
                .toLowerCase(Locale.ROOT);
        String separator = candidate.getFileSystem()
                .getSeparator()
                .toLowerCase(Locale.ROOT);
        if (
                candidateValue.equals(forbiddenValue)
                        || candidateValue.startsWith(
                                forbiddenValue + separator
                        )
        ) {
            throw new IllegalStateException(message);
        }
    }

    public record VerifiedPerformanceEnvironment(
            String environmentId,
            String databaseName,
            String imageDirectory,
            String gitCommit
    ) {
    }
}
