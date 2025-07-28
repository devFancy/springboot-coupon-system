package dev.be.coupon.infra.kafka;

public enum KafkaTopic {
    COUPON_ISSUE("coupon_issue");

    private final String topicName;

    KafkaTopic(final String topicName) {
        this.topicName = topicName;
    }

    public String getTopicName() {
        return this.topicName;
    }
}
