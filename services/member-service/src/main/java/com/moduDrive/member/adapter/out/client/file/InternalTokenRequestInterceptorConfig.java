package com.moduDrive.member.adapter.out.client.file;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Presents the shared service-to-service secret on outbound {@code /internal/**} calls. This
 * service reaches exactly one such route — {@code POST /internal/v1/namespaces} on signup — but
 * file-service now rejects every unauthenticated {@code /internal/**} request (#314), so signup
 * breaks without this. Scoped by path, so the secret never rides along on a tenant-facing call. */
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
