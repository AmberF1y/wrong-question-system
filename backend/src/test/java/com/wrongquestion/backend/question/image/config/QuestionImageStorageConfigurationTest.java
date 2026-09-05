package com.wrongquestion.backend.question.image.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestionImageStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            QuestionImageStorageConfiguration.class
                    );

    @Test
    void shouldUseDefaultQuestionImageDirectory() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(
                    "./data/question-images",
                    context.getBean(QuestionImageStorageProperties.class)
                            .getQuestionImageDirectory()
            );
        });
    }

    @Test
    void shouldBindConfiguredQuestionImageDirectory() {
        contextRunner
                .withPropertyValues(
                        "app.storage.question-image-directory=D:/images"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(
                            "D:/images",
                            context.getBean(
                                    QuestionImageStorageProperties.class
                            ).getQuestionImageDirectory()
                    );
                });
    }

    @Test
    void shouldRejectBlankQuestionImageDirectory() {
        contextRunner
                .withPropertyValues(
                        "app.storage.question-image-directory=   "
                )
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
