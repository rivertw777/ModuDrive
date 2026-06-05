package com.moduDrive.common.core.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        ApiResponse<Object> response = ApiResponse.error(e.getExceptionCase());
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(BindingResult bindingResult) {
        String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
        ApiResponse<Object> response = ApiResponse.error(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Object>> handleFeignException(FeignException feignException) throws JsonProcessingException {
        String responseJson = feignException.contentUTF8();
        Map<String, String> responseMap = objectMapper.readValue(responseJson, Map.class);
        String errorMessage = responseMap.getOrDefault("message", "외부 서비스 오류");

        ApiResponse<Object> response = ApiResponse.error(
                HttpStatus.valueOf(feignException.status()),
                errorMessage
        );
        return ResponseEntity
                .status(feignException.status())
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception e) {
        ApiResponse<Object> response = ApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
        );
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        ApiResponse<Object> response = ApiResponse.error(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

}
