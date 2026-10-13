package com.wrongquestion.backend.ai;

import static com.wrongquestion.backend.ai.AiDtos.*;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.dto.QuestionDetailResponse;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiTaskController {
    private final AiTaskService service;

    public AiTaskController(AiTaskService service) {
        this.service = service;
    }

    @GetMapping("/configuration")
    public Configuration configuration() {
        return service.configuration();
    }

    @GetMapping("/statistics")
    public Statistics statistics() {
        return service.statistics();
    }

    @GetMapping("/calls")
    public CallPage calls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return service.allCalls(page, size, status);
    }

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Task create(
            @RequestHeader("Idempotency-Key") UUID id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "rootId", required = false) Long rootId) {
        return service.create(id, file, rootId);
    }

    @GetMapping("/tasks")
    public TaskPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return service.list(page, size, status);
    }

    @GetMapping("/tasks/{id}")
    public Task get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/tasks/{id}/calls")
    public List<Call> calls(@PathVariable UUID id) {
        return service.calls(id);
    }

    @GetMapping("/tasks/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        var image = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.bytes());
    }

    @PostMapping("/tasks/{id}/retry")
    public Task retry(@PathVariable UUID id, @RequestParam int expectedRetryCount) {
        return service.retry(id, expectedRetryCount);
    }

    @PostMapping("/tasks/{id}/cancel")
    public Task cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/tasks/{id}/apply")
    public QuestionDetailResponse apply(
            @PathVariable UUID id, @RequestBody @Valid CreateQuestionRequest request) {
        return service.apply(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public MessageResponse delete(@PathVariable UUID id) {
        service.delete(id);
        return new MessageResponse("任务已删除，已保存的错题不受影响");
    }
}
