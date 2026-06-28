package com.moduDrive.gateway.client;

import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthClient {

    private final WebClient webClient;

    public AuthClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("lb://auth-service")
                .build();
    }

    public Mono<ApiResponse<ValidateTokenResponse>> validateToken(ValidateTokenRequest request) {
        return webClient.post()
                .uri("/api/v1/auth/validate-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }
}
