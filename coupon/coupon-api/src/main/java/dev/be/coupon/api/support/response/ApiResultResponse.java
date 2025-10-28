package dev.be.coupon.api.support.response;


import dev.be.coupon.api.support.error.ErrorMessage;
import dev.be.coupon.api.support.error.ErrorType;

public record ApiResultResponse<T>(ResultType resultType, T data, ErrorMessage errorMessage) {

    public static ApiResultResponse<?> success() {
        return new ApiResultResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <T> ApiResultResponse<T> success(final T data) {
        return new ApiResultResponse<>(ResultType.SUCCESS, data, null);
    }

    public static ApiResultResponse<?> error(final ErrorType errorType) {
        return new ApiResultResponse<>(ResultType.ERROR, null, new ErrorMessage(errorType));
    }

    public static ApiResultResponse<?> error(final ErrorType error, final Object errorData) {
        return new ApiResultResponse<>(ResultType.ERROR, null, new ErrorMessage(error, errorData));
    }
}
