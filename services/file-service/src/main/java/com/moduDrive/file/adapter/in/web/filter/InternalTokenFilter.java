package com.moduDrive.file.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.exception.FileExceptionCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gate on the {@code /internal/**} routes, which are service-to-service only and take the acting
 * user's id as a plain request parameter — whoever reaches them can impersonate any user outright
 * (#314). Until now the only thing keeping an arbitrary caller out was that the gateway happens not
 * to proxy that prefix (see gateway {@code RouteConfig}); that is a deployment accident, not an
 * authorization check, and anything else on the internal network could bypass it.
 *
 * <p>A caller without the shared secret is answered exactly like a missing file — same 404 /
 * {@code FILE_NOT_FOUND} body the routes themselves produce — so the response never confirms that
 * an internal route (or the file it names) exists at all.
 *
 * <p>Registered — and scoped to {@code /internal/*} — by {@link InternalTokenFilterConfig}; it is
 * deliberately not a {@code @Component}, so it stays out of the web slice tests of the
 * tenant-facing controllers that never carry this header.
 */
class InternalTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    InternalTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        if (expectedToken == null || expectedToken.isBlank()) {
            // Fail at startup rather than boot a service whose internal routes accept an empty
            // header as the password.
            throw new IllegalStateException("internal.service.token must be configured");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!matchesExpectedToken(request.getHeader(HEADER))) {
            respondNotFound(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Constant-time comparison: a byte-by-byte {@code equals} would let a caller recover the
     * secret one character at a time from the response timing. */
    private boolean matchesExpectedToken(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8));
    }

    private void respondNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(FileExceptionCase.FILE_NOT_FOUND.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(FileExceptionCase.FILE_NOT_FOUND));
    }
}
