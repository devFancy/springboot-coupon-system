package dev.be.coupon.logging.filter;

import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 모든 HTTP 요청과 응답을 로깅하고, 분산 추적을 위한 Global Trace ID를 관리하는 필터입니다.
 * 1. 요청/응답의 상세 정보(메서드, URI, 헤더, 바디 등)를 로깅합니다.
 * 2. 분산 시스템 환경에서 MDC 요청 추적을 위해 'X-Global-Trace-Id' 헤더를 확인합니다.
 */
public class HttpRequestAndResponseLoggingFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(HttpRequestAndResponseLoggingFilter.class);

    // MSA 환경에서 서비스 간 HTTP 통신 시 Global Trace ID를 전파하기 위한 표준 헤더 이름입니다.
    private static final String GLOBAL_TRACE_ID_HEADER = "X-Global-Trace-Id";
    private static final String GLOBAL_TRACE_ID_KEY = "globalTraceId";

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain) {

        final ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        final ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // 추후 외부(e.g. 게이트웨이)로부터 GlobalTraceId 헤더 값을 가져올 수 있기 때문에 설정함.
        String globalTraceId = requestWrapper.getHeader(GLOBAL_TRACE_ID_HEADER);
        if (!StringUtils.hasText(globalTraceId)) {
            globalTraceId = UUID.randomUUID().toString().substring(0, 32);
        }
        final String fixedGlobalTraceId = globalTraceId;
        MDC.put(GLOBAL_TRACE_ID_KEY, globalTraceId);

        // Sentry의 Tags 부분에 globalTraceId 추가
        Sentry.configureScope(scope -> {
            scope.setTag(GLOBAL_TRACE_ID_KEY, fixedGlobalTraceId);
        });

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
            MDC.remove(GLOBAL_TRACE_ID_KEY);
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
