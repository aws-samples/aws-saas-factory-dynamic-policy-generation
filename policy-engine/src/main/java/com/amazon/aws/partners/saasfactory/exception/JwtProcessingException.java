package com.amazon.aws.partners.saasfactory.exception;

public class JwtProcessingException extends RuntimeException{

    public JwtProcessingException() {
    }

    public JwtProcessingException(String message) {
        super(message);
    }

    public JwtProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

