package com.moduDrive.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.gateway.client.AuthClient;
import com.moduDrive.gateway.common.AuthErrorAttributeUtils;
import com.moduDrive.gateway.common.AuthExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
class CustomServerSecurityContextRepository implements ServerSecurityContextRepository {

    private final AuthClient authClient;

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return null;
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_AUTH_TOKEN);
            return Mono.empty();
        }

        ValidateTokenRequest validateTokenRequest = new ValidateTokenRequest(authHeader.substring(7));

        return authClient.validateToken(validateTokenRequest)
                .map(apiResponse -> {
                    ValidateTokenResponse authData = apiResponse.getData();
                    String roles = String.join(",", authData.memberRoles());

                    exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                headers.add("X_USER_ID", authData.memberId());
                                headers.add("X_USER_ROLE", roles);
                            }))
                            .build();

                    Authentication authentication = createAuthenticationToken(authData.memberId(), roles);
                    return new SecurityContextImpl(authentication);
                })
                .cast(SecurityContext.class)
                .onErrorResume(WebClientResponseException.class, e -> handleWebClientException(exchange, e))
                .onErrorResume(Exception.class, e -> Mono.empty());
    }

    private static Authentication createAuthenticationToken(String memberId, String roles) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roles));
        return new UsernamePasswordAuthenticationToken(
                memberId, null, authorities
        );
    }

    private Mono<SecurityContext> handleWebClientException(ServerWebExchange exchange, WebClientResponseException e) {
        try {
            String responseJson = e.getResponseBodyAsString();
            ObjectMapper objectMapper = new ObjectMapper();
            String status = objectMapper.readTree(responseJson).get("status").asText();
            String message = objectMapper.readTree(responseJson).get("message").asText();
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, status, message);
        } catch (Exception jsonProcessingException) {
            log.error("Content 파싱 실패: {}", jsonProcessingException.getMessage());
        }
        return Mono.empty();
    }

}
