package com.homerun.common.model;

/**
 * Describes how a mock expectation should behave when invoked.
 */
public enum MockBehaviorType {

    /** Deserialize {@code serializedResponse} and return it as-is. */
    SUCCESS,

    /** Throw the domain exception identified by {@code errorClassName}. */
    BUSINESS_ERROR,

    /** Throw an HTTP-level exception with {@code httpStatus}. */
    HTTP_ERROR,

    /** Sleep for {@code delayMillis} then throw a timeout exception. */
    TIMEOUT,

    /** Throw a parse exception without reading the response payload. */
    MALFORMED_RESPONSE
}
