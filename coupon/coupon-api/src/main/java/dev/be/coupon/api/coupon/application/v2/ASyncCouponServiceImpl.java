package dev.be.coupon.api.coupon.application.v2;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.exception.CouponNotFoundException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.infra.redis.v2.CouponEntryCountRepository;
import dev.be.coupon.infra.redis.v2.CouponV2CacheRepository;
import dev.be.coupon.infra.redis.v2.CouponWaitingQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class ASyncCouponServiceImpl implements AsyncCouponService {

    private static final int ISSUE_CANDIDATE_RATIO = 10;
    private final CouponRepository couponRepository;
    private final CouponWaitingQueueRepository waitingQueueRepository;
    private final CouponV2CacheRepository couponV2CacheRepository;
    private final CouponEntryCountRepository couponEntryCountRepository;

    private final Logger log = LoggerFactory.getLogger(ASyncCouponServiceImpl.class);

    public ASyncCouponServiceImpl(final CouponRepository couponRepository,
                                  final CouponWaitingQueueRepository waitingQueueRepository,
                                  final CouponV2CacheRepository couponV2CacheRepository,
                                  final CouponEntryCountRepository couponEntryCountRepository) {
        this.couponRepository = couponRepository;
        this.waitingQueueRepository = waitingQueueRepository;
        this.couponV2CacheRepository = couponV2CacheRepository;
        this.couponEntryCountRepository = couponEntryCountRepository;
    }

    // 입장 통제(INCR) -> 대기(Sorted set) -> 처리(Scheduler)
    public CouponIssueRequestResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();

        final long issueLimit = getIssueLimitWithCaching(couponId);

        if (issueLimit <= 0) {
            log.warn("유효한 발급 한도가 설정되지 않았습니다.");
            return CouponIssueRequestResult.SOLD_OUT;
        }

        // 1. 중복 참여 검증 및 줄 세우기
        Boolean isFirstEntryForUser = waitingQueueRepository.add(couponId, userId);
        if (!isFirstEntryForUser) {
            log.info("중복 참여 요청 - userId: {}", userId);
            return CouponIssueRequestResult.DUPLICATE;
        }

        // 2. 원자적 카운터를 이용해 절대적인 입장 순번(번호표) 획득 (INCR)
        long entryOrder = couponEntryCountRepository.increment(couponId);

        // 3. 선착순 마감 여부 판별
        if (entryOrder > issueLimit) {
            log.warn("선착순 마감(절대 순번 기준) - userId: {}, entryOrder: {}, limit: {}", userId, entryOrder, issueLimit);
            waitingQueueRepository.remove(couponId, Set.of(userId.toString()));
            return CouponIssueRequestResult.SOLD_OUT;
        }

        // 4. 선착순 성공
        log.info("쿠폰 발급 요청을 대기열에 추가했습니다 - userId: {}, entryOrder: {}", userId, entryOrder);
        return CouponIssueRequestResult.SUCCESS;
    }

    private long getIssueLimitWithCaching(final UUID couponId) {
        Integer totalQuantity = couponV2CacheRepository.getTotalQuantity(couponId);

        if (totalQuantity != null) {
            return (long) totalQuantity * ISSUE_CANDIDATE_RATIO;
        }

        log.info("캐시 미스 발생. DB에서 쿠폰 정보를 조회합니다. couponId: {}", couponId);
        Coupon couponFromDb = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("ID: " + couponId + "에 해당하는 쿠폰을 찾을 수 없습니다."));

        couponV2CacheRepository.save(couponFromDb);

        return couponFromDb.getTotalQuantity();
    }
}
