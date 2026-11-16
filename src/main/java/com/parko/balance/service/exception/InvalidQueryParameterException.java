package com.parko.balance.service.exception;

public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}
