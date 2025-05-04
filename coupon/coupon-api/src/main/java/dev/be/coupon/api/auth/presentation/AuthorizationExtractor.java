package dev.be.coupon.api.auth.presentation;

import dev.be.coupon.api.auth.exception.EmptyAuthorizationHeaderException;
import dev.be.coupon.api.auth.exception.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.util.Objects;

public class AuthorizationExtractor {

    private static final String BEARER_TYPE = "Bearer ";

    public static String extract(final HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(Objects.isNull(authorizationHeader)) {
            throw new EmptyAuthorizationHeaderException();
        }

        validationAuthorizationFormat(authorizationHeader);
        return authorizationHeader.substring(BEARER_TYPE.length()).trim(); // accessToken 문자열
    }

    private static void validationAuthorizationFormat(final String authorizationHeader) {
        if(!authorizationHeader.toLowerCase().startsWith(BEARER_TYPE.toLowerCase())) {
            throw new InvalidTokenException("token 형식이 올바르지 않습니다.");
        }
    }
}
