package dev.be.core.api.support.response;

import dev.be.core.api.support.error.ErrorMessage;
import dev.be.core.api.support.error.ErrorType;

public record CommonResponse<T>(ResultType resultType, T data, ErrorMessage errorMessage) {

    public static CommonResponse<?> success() {
        return new CommonResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <T> CommonResponse<T> success(final T data) {
        return new CommonResponse<>(ResultType.SUCCESS, data, null);
    }

    public static CommonResponse<?> error(final ErrorType errorType) {
        return new CommonResponse<>(ResultType.ERROR, null, new ErrorMessage(errorType));
    }

    public static CommonResponse<?> error(final ErrorType error, final Object errorData) {
        return new CommonResponse<>(ResultType.ERROR, null, new ErrorMessage(error, errorData));
    }
}
