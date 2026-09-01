package com.parko.balance.service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(OwnershipMismatchException.class)
    public ResponseEntity<ApiError> handleOwnershipMismatch(OwnershipMismatchException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("OWNERSHIP_MISMATCH", ex.getMessage()));
    }

    @ExceptionHandler(TopUpAmountExceededException.class)
    public ResponseEntity<ApiError> handleAmountExceeded(TopUpAmountExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("AMOUNT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ApiError> handleAmqpUnavailable(AmqpException ex) {
        log.error("RabbitMQ no disponible al publicar evento", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("MESSAGING_UNAVAILABLE", "No se pudo procesar la operación, intente nuevamente"));
    }
}
