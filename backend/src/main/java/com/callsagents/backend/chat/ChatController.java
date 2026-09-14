package com.callsagents.backend.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatUsageLimiter usageLimiter;
    private final WidgetDomainGuard domainGuard;

    public ChatController(ChatService chatService, ChatUsageLimiter usageLimiter, WidgetDomainGuard domainGuard) {
        this.chatService = chatService;
        this.usageLimiter = usageLimiter;
        this.domainGuard = domainGuard;
    }

    @GetMapping("/start")
    public ResponseEntity<?> start(@RequestParam(value = "sessionId", required = false) String sessionId,
                                   @RequestParam(value = "businessId", required = false) UUID businessId,
                                   HttpServletRequest request) {
        if (isRateLimited(businessId)) {
            return tooManyRequests();
        }
        if (businessId != null && !domainGuard.isAllowed(businessId, request.getHeader("Referer"))) {
            return domainNotAllowed();
        }
        String sid = sessionId != null && !sessionId.isBlank() ? sessionId : UUID.randomUUID().toString();
        log.info("Chat start: sessionId={} businessId={}", sid, businessId);
        ChatResponse response = chatService.start(sid, businessId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (isRateLimited(request.businessId())) {
            return tooManyRequests();
        }
        if (request.businessId() != null
            && !domainGuard.isAllowed(request.businessId(), httpRequest.getHeader("Referer"))) {
            return domainNotAllowed();
        }
        log.info("Chat message: sessionId={} message={} businessId={}",
                 request.sessionId(), request.message(), request.businessId());

        ChatResponse response = chatService.processMessage(
            request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString(),
            request.message(),
            request.businessId()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    private boolean isRateLimited(UUID businessId) {
        // businessId null -> per-IP limit only (RateLimitFilter), legacy behavior.
        return businessId != null && !usageLimiter.tryAcquire(businessId);
    }

    private ResponseEntity<Map<String, String>> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("error", "Too many requests. Please try again later."));
    }

    private ResponseEntity<Map<String, String>> domainNotAllowed() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "Domain not allowed"));
    }
}