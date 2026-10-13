package com.wrongquestion.backend.rag;

import static com.wrongquestion.backend.rag.RagDtos.*;

import com.wrongquestion.backend.ai.AiDtos;
import com.wrongquestion.backend.common.dto.MessageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService service;
    private final RagIndexService index;

    public RagController(RagService service, RagIndexService index) {
        this.service = service;
        this.index = index;
    }

    @GetMapping("/configuration")
    public Configuration configuration() {
        return service.configuration();
    }

    @PostMapping("/sessions")
    public Session create(@RequestHeader("Idempotency-Key") UUID id) {
        return service.createSession(id);
    }

    @GetMapping("/sessions")
    public SessionPage sessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.sessions(page, size);
    }

    @DeleteMapping("/sessions/{id}")
    public MessageResponse delete(@PathVariable UUID id) {
        service.deleteSession(id);
        return new MessageResponse("学习对话与调用记录已删除");
    }

    @PostMapping("/sessions/{sessionId}/requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Request ask(
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") UUID id,
            @RequestBody @Valid Ask input) {
        return service.ask(id, sessionId, input);
    }

    @GetMapping("/sessions/{sessionId}/requests")
    public RequestPage history(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.requests(sessionId, page, size);
    }

    @GetMapping("/requests/{id}")
    public Request get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/requests/{id}/calls")
    public List<AiDtos.Call> calls(@PathVariable UUID id) {
        return service.calls(id);
    }

    @PostMapping("/requests/{id}/retry")
    public Request retry(@PathVariable UUID id, @RequestParam int expectedRetryCount) {
        return service.retry(id, expectedRetryCount);
    }

    @PostMapping("/requests/{id}/cancel")
    public Request cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @GetMapping("/index")
    public IndexStatus status(@RequestParam(required = false) Long rootId) {
        return index.status(rootId);
    }

    @PostMapping("/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Request build(
            @RequestHeader("Idempotency-Key") UUID id, @RequestBody @Valid IndexCommand command) {
        return service.build(id, command.rootId());
    }
}
