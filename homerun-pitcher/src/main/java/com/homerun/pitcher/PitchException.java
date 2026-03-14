package com.homerun.pitcher;

/**
 * Thrown when the pitcher fails to serialize or persist an expectation.
 */
public class PitchException extends RuntimeException {

    public PitchException(String message) {
        super(message);
    }

    public PitchException(String message, Throwable cause) {
        super(message, cause);
    }
}
