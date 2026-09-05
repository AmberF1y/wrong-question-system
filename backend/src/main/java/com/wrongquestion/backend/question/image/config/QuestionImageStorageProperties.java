package com.wrongquestion.backend.question.image.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public class QuestionImageStorageProperties {

    @NotBlank
    private String questionImageDirectory = "./data/question-images";

    public String getQuestionImageDirectory() {
        return questionImageDirectory;
    }

    public void setQuestionImageDirectory(String questionImageDirectory) {
        this.questionImageDirectory = questionImageDirectory;
    }
}
