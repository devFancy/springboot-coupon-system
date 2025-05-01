package deb.be.coupon;

/**
 * 사용자 권한을 정의하는 Enum.
 * - USER: 일반 사용자
 * - ADMIN: 관리자
 *
 * [주의]
 * 이 Enum 은 API 계층에 직접 노출되지 않으며,
 * 외부 노출 시에는 반드시 String 형태로 변환해 사용해야 합니다.
 * 예: userRole.name().toLowerCase()
 */
public enum UserRole {
    USER, ADMIN
}
