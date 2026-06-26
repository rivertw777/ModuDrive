package com.moduDrive.common.core.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApiResponse<T> {

    private HttpStatus status;
    private String message;
    private T data;

    ApiResponse() {}

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(HttpStatus.OK)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .status(HttpStatus.OK)
                .message("success")
                .build();
    }

    public static <T> ApiResponse<T> error(ExceptionCase exceptionCase) {
        return ApiResponse.<T>builder()
                .status(exceptionCase.getHttpStatus())
                .message(exceptionCase.getMessage())
                .build();
    }

    public static <T> ApiResponse<T> error(HttpStatus httpStatus, String message) {
        return ApiResponse.<T>builder()
                .status(httpStatus)
                .message(message)
                .build();
    }

}