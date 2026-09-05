package com.wrongquestion.backend.question.image.controller;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.question.image.dto.QuestionImageResponse;
import com.wrongquestion.backend.question.image.model.QuestionImageContent;
import com.wrongquestion.backend.question.image.service.QuestionImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/questions/{questionId}/image")
public class QuestionImageController {

    private final QuestionImageService questionImageService;

    public QuestionImageController(QuestionImageService questionImageService) {
        this.questionImageService = questionImageService;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuestionImageResponse uploadOrReplace(
            @PathVariable Long questionId,
            @RequestParam(name = "file", required = false) MultipartFile file
    ) {
        return questionImageService.uploadOrReplace(questionId, file);
    }

    @GetMapping
    public ResponseEntity<org.springframework.core.io.Resource> load(
            @PathVariable Long questionId
    ) {
        QuestionImageContent content = questionImageService.load(questionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.resource());
    }

    @DeleteMapping
    public MessageResponse remove(@PathVariable Long questionId) {
        return questionImageService.remove(questionId);
    }
}
