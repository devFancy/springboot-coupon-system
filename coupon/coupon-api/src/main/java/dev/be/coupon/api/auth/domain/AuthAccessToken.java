package dev.be.coupon.api.auth.domain;

public class AuthAccessToken {

    private String accessToken;

    public AuthAccessToken(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
