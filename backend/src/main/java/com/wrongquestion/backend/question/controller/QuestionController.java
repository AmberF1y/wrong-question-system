package com.wrongquestion.backend.question.controller;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.dto.QuestionDetailResponse;
import com.wrongquestion.backend.question.dto.QuestionPageResponse;
import com.wrongquestion.backend.question.dto.UpdateQuestionRequest;
import com.wrongquestion.backend.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<QuestionDetailResponse> create(
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(questionService.create(request));
    }

    @GetMapping("/{id}")
    public QuestionDetailResponse getById(@PathVariable Long id) {
        return questionService.getById(id);
    }

    @GetMapping
    public QuestionPageResponse getPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String subject
    ) {
        return questionService.getPage(page, size, subject);
    }

    @PutMapping("/{id}")
    public QuestionDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuestionRequest request
    ) {
        return questionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        return questionService.delete(id);
    }
}
