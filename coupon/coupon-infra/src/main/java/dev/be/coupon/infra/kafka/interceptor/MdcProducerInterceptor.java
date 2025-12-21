package dev.be.coupon.infra.kafka.interceptor;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MdcProducerInterceptor implements ProducerInterceptor<String, Object> {

    private static final String GLOBAL_TRACE_ID_KEY = "globalTraceId";
    private static final Logger log = LoggerFactory.getLogger(MdcProducerInterceptor.class);

    // NOTE: Kafka 브로커로 메시지를 보내기 전에 호출합니다.
    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        String globalTraceId = MDC.get(GLOBAL_TRACE_ID_KEY);
        if (globalTraceId != null) {
            record.headers().add(GLOBAL_TRACE_ID_KEY, globalTraceId.getBytes(StandardCharsets.UTF_8));
            if (log.isDebugEnabled()) {
                log.debug("[onSend] MDC 헤더 추가 [{}]: {}", GLOBAL_TRACE_ID_KEY, globalTraceId);
            }
        }
        return record;
    }

    /**
     * NOTE: 프로듀서가 보낸 메시지에 대해 Kafka 브로커로부터 응답(Acknowledgement)을 받았을 때 호출됩니다.
     * - 메시지 전송 성공/실패에 대한 별도의 메트릭(metric)을 수집하고 싶을 때
     * - 전송 결과에 따라 특정 자원을 정리하거나 다른 동작을 트리거해야 할 때
     */
    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
    }

    // Kafka 프로듀서가 종료될 때 호출됩니다.
    @Override
    public void close() {
    }

    // 프로듀서가 생성될 때 인터셉터가 초기화되면서 딱 한 번 호출됩니다.
    @Override
    public void configure(Map<String, ?> map) {
    }
}
