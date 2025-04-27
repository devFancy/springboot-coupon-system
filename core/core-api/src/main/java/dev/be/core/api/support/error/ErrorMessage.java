package dev.be.core.api.support.error;


public class ErrorMessage {

    private final String code;
    private final String message;
    private final Object data;

    public ErrorMessage(final ErrorType errorType) {
        this.code = errorType.getCode().name();
        this.message = errorType.getMessage();
        this.data = null;
    }

    public ErrorMessage(final ErrorType errorType, final Object data) {
        this.code = errorType.getCode().name();
        this.message = errorType.getMessage();
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
