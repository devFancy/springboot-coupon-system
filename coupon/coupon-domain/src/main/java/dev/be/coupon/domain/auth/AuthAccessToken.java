package dev.be.coupon.domain.auth;

public class AuthAccessToken {

    private String accessToken;

    public AuthAccessToken(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
