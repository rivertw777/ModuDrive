package com.moduDrive.common.core.exception;

import org.springframework.http.HttpStatus;

public interface ExceptionCase {
    HttpStatus getHttpStatus();
    String getMessage();
}