package com.moduDrive.gateway.client;

import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "auth-service")
public interface AuthFeignClient {

    @PostMapping("/api/v1/auth/validate-token")
    Mono<ApiResponse<ValidateTokenResponse>> validateToken(ValidateTokenRequest validateTokenRequest);

}