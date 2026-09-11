package com.moduDrive.file.adapter.out.client.storage;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Presents the shared service-to-service secret on outbound {@code /internal/**} calls, which
 * storage-service now demands (#332 — {@code PurgeStoredFileController} takes the acting user's
 * id as a parameter, so it must not be callable by anything that merely reached the network).
 * Scoped by path rather than by client — {@code StorageServiceClient} has only this one internal
 * route today, but scoping by path means the secret still only ever leaves this service on an
 * {@code /internal/**} call, whichever Feign client it's added to later. */
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
