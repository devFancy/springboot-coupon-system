package dev.be.core.api.support.filter.v1;

import org.springframework.http.HttpStatus;

public record HttpLogMessage(
        String httpMethod,
        String url,
        HttpStatus httpStatus,
        String headers,
        String requestBody,
        String responseBody
) {
    // ...
}
