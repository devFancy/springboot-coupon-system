package dev.be.coupon.api.support.response;


import dev.be.coupon.api.support.error.ErrorMessage;
import dev.be.coupon.api.support.error.ErrorType;

public record ApiResponse<T>(ResultType resultType, T data, ErrorMessage errorMessage) {

    public static ApiResponse<?> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <T> ApiResponse<T> success(final T data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null);
    }

    public static ApiResponse<?> error(final ErrorType errorType) {
        return new ApiResponse<>(ResultType.ERROR, null, new ErrorMessage(errorType));
    }

    public static ApiResponse<?> error(final ErrorType error, final Object errorData) {
        return new ApiResponse<>(ResultType.ERROR, null, new ErrorMessage(error, errorData));
    }
}
