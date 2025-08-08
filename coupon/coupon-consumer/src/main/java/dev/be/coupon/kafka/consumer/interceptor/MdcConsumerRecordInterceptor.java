package dev.be.coupon.kafka.consumer.interceptor;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class MdcConsumerRecordInterceptor implements RecordInterceptor<String, CouponIssueMessage> {

    private static final String GLOBAL_TRACE_ID_KEY = "globalTraceId";
    private static final Logger log = LoggerFactory.getLogger(MdcConsumerRecordInterceptor.class);

    @Override
    public ConsumerRecord<String, CouponIssueMessage> intercept(
            @NonNull ConsumerRecord<String, CouponIssueMessage> record,
            @NonNull Consumer<String, CouponIssueMessage> consumer) {
        Header header = record.headers().lastHeader(GLOBAL_TRACE_ID_KEY);
        if (Objects.nonNull(header)) {
            String globalTraceId = new String(header.value(), StandardCharsets.UTF_8);
            MDC.put(GLOBAL_TRACE_ID_KEY, globalTraceId);
            if (log.isDebugEnabled()) {
                log.debug("[MdcConsumerRecordInterceptor] MDC 컨텍스트 설정 [{}]: {}", GLOBAL_TRACE_ID_KEY, globalTraceId);
            }
        }
        return record;
    }

    @Override
    public void success(
            @NonNull ConsumerRecord<String, CouponIssueMessage> record,
            @NonNull Consumer<String, CouponIssueMessage> consumer) {
        MDC.remove(GLOBAL_TRACE_ID_KEY);
    }

    @Override
    public void failure(
            @NonNull ConsumerRecord<String, CouponIssueMessage> record,
            @NonNull Exception exception,
            @NonNull Consumer<String, CouponIssueMessage> consumer) {
        MDC.remove(GLOBAL_TRACE_ID_KEY);
    }
}
