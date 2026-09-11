package com.moduDrive.common.core.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ExceptionCase exceptionCase;
    /** Optional extra context for the error response body (e.g. FileAccessGuard attaching
     * isDirectory to a FILE_ACCESS_DENIED so the client can word its message accordingly) — null
     * for the overwhelming majority of call sites that need nothing beyond the case's own
     * status/message. */
    private final Object data;

    public BusinessException(ExceptionCase exceptionCase) {
        this(exceptionCase, null);
    }

    public BusinessException(ExceptionCase exceptionCase, Object data) {
        super(exceptionCase.getMessage());
        this.exceptionCase = exceptionCase;
        this.data = data;
    }

}