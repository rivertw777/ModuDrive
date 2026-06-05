package com.moduDrive.gateway.common;

import com.moduDrive.common.core.exception.ExceptionCase;
import org.springframework.web.server.ServerWebExchange;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class AuthErrorAttributeUtils {

    public static final String STATUS_ATTRIBUTE = "AUTH_ERROR_RESPONSE_STATUS";
    public static final String MESSAGE_ATTRIBUTE = "AUTH_ERROR_RESPONSE_MESSAGE";

    private AuthErrorAttributeUtils() {
    }

    public static void setAuthErrorAttribute(ServerWebExchange exchange, ExceptionCase exceptionCase) {
        exchange.getAttributes().put(STATUS_ATTRIBUTE, exceptionCase.getHttpStatus().name());
        exchange.getAttributes().put(MESSAGE_ATTRIBUTE, exceptionCase.getMessage());
    }

    public static void setAuthErrorAttribute(ServerWebExchange exchange, String status, String message) {
        exchange.getAttributes().put(STATUS_ATTRIBUTE, status);
        exchange.getAttributes().put(MESSAGE_ATTRIBUTE, message);
    }

    public static Tuple2<String, String> getAuthErrorAttribute(ServerWebExchange exchange) {
        String status = exchange.getAttributeOrDefault(STATUS_ATTRIBUTE, AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name());
        String message = exchange.getAttributeOrDefault(MESSAGE_ATTRIBUTE, AuthExceptionCase.UNAUTHORIZED.getMessage());
        return Tuples.of(status, message);
    }

}
