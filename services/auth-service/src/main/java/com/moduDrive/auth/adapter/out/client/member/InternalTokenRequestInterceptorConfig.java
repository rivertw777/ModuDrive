package com.moduDrive.auth.adapter.out.client.member;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Presents the shared service-to-service secret on outbound {@code /internal/**} calls, which
 * member-service now demands (#332 — {@code AuthenticateMemberController}/
 * {@code GetMemberStatusController} were reachable by anything on the internal network without
 * this). Scoped by path so the secret only ever leaves this service on internal routes. */
@Configuration
class InternalTokenRequestInterceptorConfig {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String HEADER = "X-Internal-Token";

    @Bean
    RequestInterceptor internalTokenRequestInterceptor(
            @Value("${internal.service.token}") String internalServiceToken) {
        return template -> {
            if (template.path().startsWith(INTERNAL_PATH_PREFIX)) {
                template.header(HEADER, internalServiceToken);
            }
        };
    }
}
