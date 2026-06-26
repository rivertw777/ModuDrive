package com.moduDrive.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@RequiredArgsConstructor
@EnableWebFluxSecurity
@Configuration
class SecurityConfig {

    private final CustomServerSecurityContextRepository securityContextRepository;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/v1/member/sign-up").permitAll()
                        .pathMatchers("/api/v1/auth/login").permitAll()
                        .pathMatchers("/api/v1/auth/validate-token").permitAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**",
                                "/*/v3/api-docs", "/*/v3/api-docs/**").permitAll()
                        .anyExchange().authenticated()
                )
                .securityContextRepository(securityContextRepository)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }

}
