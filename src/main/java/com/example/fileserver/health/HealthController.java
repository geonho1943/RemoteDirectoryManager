package com.example.fileserver.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    // 서비스 상태 확인용 응답을 반환한다.
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("HERE I AM");
    }

    // 헬스 체크 상태 문자열을 담는 응답 본문이다.
    public record HealthResponse(String status) {
    }
}
