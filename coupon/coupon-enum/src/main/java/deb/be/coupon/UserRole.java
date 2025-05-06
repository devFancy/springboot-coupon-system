package deb.be.coupon;

/**
 * 사용자 권한을 정의하는 Enum.
 * - USER: 일반 사용자
 * - ADMIN: 관리자
 *
 * [주의]
 * 이 Enum 은 내부 도메인 로직에서는 자유롭게 사용 가능하나,
 * 외부 API 응답에서는 enum 값을 직접 노출하지 말고, 문자열로 가공해 사용해야 합니다.
 * 예: userRole.name().toLowerCase()
 */
public enum UserRole {
    USER, ADMIN
}
