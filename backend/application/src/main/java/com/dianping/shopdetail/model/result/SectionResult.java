package com.dianping.shopdetail.model.result;
import java.util.function.Function;
/** 页面局部结果；不可用时 data 为空，不伪造零值或成功空列表。 */
public record SectionResult<T>(String status, T data, String message) {
    public static <T> SectionResult<T> success(T data, boolean empty) {
        return new SectionResult<>(empty ? "EMPTY" : "SUCCESS", data, null);
    }
    public static <T> SectionResult<T> unavailable(String status, String message) {
        return new SectionResult<>(status, null, message);
    }
    public boolean available() { return status.equals("SUCCESS") || status.equals("EMPTY") || status.equals("PARTIAL"); }
    public <R> SectionResult<R> map(Function<T, R> mapper) {
        return new SectionResult<>(status, data == null ? null : mapper.apply(data), message);
    }
}
