package dev.be.core.api.support.filter.v1;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 모든 필터 중 가장 먼저 실행되도록 설정 (요청/응답 전체를 로깅하기 위함)
public class HttpRequestAndResponseLoggingFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(HttpRequestAndResponseLoggingFilter.class);
    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain) {

        final ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        final ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        final String traceId = UUID.randomUUID().toString().substring(0, 32);

        MDC.put(TRACE_ID, traceId);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);

            // HttpLogMessage 생성
            HttpLogMessage logMessage = new HttpLogMessage(
                    requestWrapper.getMethod(),
                    requestWrapper.getRequestURI(),
                    HttpStatus.valueOf(responseWrapper.getStatus()),
                    getRequestHeaders(requestWrapper),
                    getRequestBody(requestWrapper),
                    getResponseBody(responseWrapper)
            );

            log.info("\n{}", toPrettierLog(logMessage));

        } catch (Exception e) {
            handleException(e);
        } finally {
            try {
                responseWrapper.copyBodyToResponse();
            } catch (IOException copyException) {
                log.error("[HttpRequestAndResponseLoggingFilter] I/O exception occurred while copying response body", copyException);
            }
            MDC.remove(TRACE_ID);
        }
    }

    // instanceof → 패턴 매칭 with 변수 바인딩 (Java 16+)
    // 관련 링크: https://docs.oracle.com/en/java/javase/16/language/pattern-matching-instanceof-operator.html
    private void handleException(final Exception e) {
        if (e instanceof IOException ioEx) {
            log.error("[HttpRequestAndResponseLoggingFilter] I/O exception occurred", ioEx);
            throw new RuntimeException("I/O error occurred while processing request/response", ioEx);
        } else if (e instanceof ServletException servletEx) {
            log.error("[HttpRequestAndResponseLoggingFilter] Servlet exception occurred", servletEx);
            throw new RuntimeException("Servlet error occurred while processing request/response", servletEx);
        } else {
            log.error("[HttpRequestAndResponseLoggingFilter] Unknown exception occurred", e);
            throw new RuntimeException("Unknown error occurred while processing request/response", e);
        }
    }

    private String toPrettierLog(final HttpLogMessage msg) {
        // Java 15+ 텍스트 블록 (""") 사용
        // 관련 링크: https://docs.oracle.com/en/java/javase/15/text-blocks/index.html
        return """
                [REQUEST] %s %s [RESPONSE - STATUS: %s]
                >> HEADERS: %s
                >> REQUEST_BODY: %s
                >> RESPONSE_BODY: %s
                """.formatted(
                msg.httpMethod(), msg.url(), msg.httpStatus(),
                msg.headers(), msg.requestBody(), msg.responseBody()
        );
    }

    private String getRequestHeaders(final HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .map(name -> name + ": " + request.getHeader(name))
                .collect(Collectors.joining("; "));
    }

    private String getRequestBody(final ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        return (buf.length > 0) ? new String(buf, StandardCharsets.UTF_8) : "";
    }

    private String getResponseBody(final ContentCachingResponseWrapper response) {
        byte[] buf = response.getContentAsByteArray();
        return (buf.length > 0) ? new String(buf, StandardCharsets.UTF_8) : "";
    }
}
