package com.stocktrade.common;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "成功", data);
    }

    public static Result<Void> success() {
        return new Result<>(0, "成功", null);
    }

    public static Result<Void> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
