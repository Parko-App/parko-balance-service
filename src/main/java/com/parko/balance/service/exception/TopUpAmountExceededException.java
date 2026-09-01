package com.parko.balance.service.exception;

public class TopUpAmountExceededException extends RuntimeException {

    public TopUpAmountExceededException(String message) {
        super(message);
    }
}
