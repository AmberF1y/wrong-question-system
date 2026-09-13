package com.wrongquestion.backend.performance.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.performance")
public class PerformanceSafetyProperties {

    @NotBlank
    @Pattern(
            regexp = "^wrong_question_system_perf_[0-9]{8}(?:_[a-z0-9_]+)?$",
            message = "expected-database必须是专用性能测试数据库"
    )
    private String expectedDatabase;

    @NotBlank
    @Pattern(
            regexp = "^perf-[a-z0-9][a-z0-9-]{2,63}$",
            message = "environment-id格式不正确"
    )
    private String environmentId;

    @NotBlank
    @Pattern(
            regexp = "^[0-9a-f]{40}$",
            message = "git-commit必须是完整的40位提交哈希"
    )
    private String gitCommit;

    @NotBlank
    private String repositoryRoot;

    @NotBlank
    private String productionImageDirectory =
            "D:/WrongQuestionData/question-images";

    @NotBlank
    private String productionBackupDirectory =
            "D:/WrongQuestionBackups";

    public String getExpectedDatabase() {
        return expectedDatabase;
    }

    public void setExpectedDatabase(String expectedDatabase) {
        this.expectedDatabase = expectedDatabase;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getRepositoryRoot() {
        return repositoryRoot;
    }

    public void setRepositoryRoot(String repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
    }

    public String getProductionImageDirectory() {
        return productionImageDirectory;
    }

    public void setProductionImageDirectory(
            String productionImageDirectory
    ) {
        this.productionImageDirectory = productionImageDirectory;
    }

    public String getProductionBackupDirectory() {
        return productionBackupDirectory;
    }

    public void setProductionBackupDirectory(
            String productionBackupDirectory
    ) {
        this.productionBackupDirectory = productionBackupDirectory;
    }
}
