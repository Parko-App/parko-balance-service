package com.parko.balance.service.exception;

public class OwnershipMismatchException extends RuntimeException {

    public OwnershipMismatchException(String message) {
        super(message);
    }
}
