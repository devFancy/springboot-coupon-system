package dev.be.coupon.api.auth.application;

public interface TokenProvider {

    String createAccessToken(final String payLoad);

    void validateToken(final String accessToken);

    String getPayLoad(final String accessToken);
}
