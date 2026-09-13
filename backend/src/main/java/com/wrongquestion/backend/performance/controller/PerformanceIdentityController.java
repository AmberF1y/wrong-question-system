package com.wrongquestion.backend.performance.controller;

import com.wrongquestion.backend.performance.config.PerformanceIsolationGuard;
import com.wrongquestion.backend.performance.config.PerformanceIsolationGuard.VerifiedPerformanceEnvironment;
import com.wrongquestion.backend.performance.dto.PerformanceIdentityResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("performance")
@RestController
@RequestMapping("/api/performance/identity")
public class PerformanceIdentityController {

    private static final String ENVIRONMENT_HEADER =
            "X-Performance-Environment";

    private final PerformanceIsolationGuard isolationGuard;

    public PerformanceIdentityController(
            PerformanceIsolationGuard isolationGuard
    ) {
        this.isolationGuard = isolationGuard;
    }

    @GetMapping
    public PerformanceIdentityResponse identity(
            @RequestHeader(ENVIRONMENT_HEADER) String requestedEnvironment
    ) {
        VerifiedPerformanceEnvironment verified =
                isolationGuard.getVerifiedEnvironment();
        if (!verified.environmentId().equals(requestedEnvironment)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "性能测试环境标识不匹配"
            );
        }
        return new PerformanceIdentityResponse(
                "performance",
                verified.environmentId(),
                verified.databaseName(),
                verified.imageDirectory(),
                verified.gitCommit()
        );
    }
}
