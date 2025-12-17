package dev.be.coupon.api.auth.application.impl;

import dev.be.coupon.api.auth.application.TokenProvider;
import dev.be.coupon.api.support.error.AuthException;
import dev.be.coupon.api.support.error.ErrorType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityInMilliseconds;

    public JwtTokenProvider(@Value("${security.jwt.token.secret-key}") final String secretKey, @Value("${security.jwt.token.access.expire-length}") long accessTokenValidityInMilliseconds) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityInMilliseconds = accessTokenValidityInMilliseconds;
    }

    @Override
    public String createAccessToken(final String payLoad) {
        return createToken(payLoad, accessTokenValidityInMilliseconds);
    }

    public String createToken(final String payLoad, final Long validityInMilliseconds) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setSubject(payLoad)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public void validateToken(final String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            if (claims.getBody().getExpiration().before(new Date())) { // 만료 시간이 현재 시간보다 이전 시간인지 확인하는 조건문
                throw new AuthException(ErrorType.AUTH_TOKEN_EXPIRED);
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }
    }

    public String getPayLoad(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
