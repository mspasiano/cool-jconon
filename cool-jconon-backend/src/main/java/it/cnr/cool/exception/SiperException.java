package it.cnr.cool.exception;

/**
 * Created by francesco on 02/12/16.
 */
public class SiperException extends RuntimeException {
    public SiperException(String message, Throwable cause) {
        super(message, cause);
    }

    public SiperException(String message) {
        super(message);
    }
}