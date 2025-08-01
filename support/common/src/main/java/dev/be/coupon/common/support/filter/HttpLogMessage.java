package dev.be.coupon.common.support.filter;

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
