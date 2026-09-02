package com.wrongquestion.backend.knowledge.controller;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.knowledge.dto.CreateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointTreeNodeResponse;
import com.wrongquestion.backend.knowledge.dto.UpdateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.service.KnowledgePointService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-points")
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    public KnowledgePointController(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    @GetMapping("/tree")
    public List<KnowledgePointTreeNodeResponse> getTree() {
        return knowledgePointService.getTree();
    }

    @PostMapping
    public ResponseEntity<KnowledgePointResponse> create(
            @Valid @RequestBody CreateKnowledgePointRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(knowledgePointService.create(request));
    }

    @PutMapping("/{id}")
    public KnowledgePointResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgePointRequest request
    ) {
        return knowledgePointService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        return knowledgePointService.delete(id);
    }
}
