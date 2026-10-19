package com.dianping.shop.shopdetail.model.result;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 页面局部结果；不可用时 data 为空，不伪造零值或成功空列表。
 *
 * @param <T> 当前业务数据类型
 * @param status 业务或节点状态
 * @param data 业务数据
 * @param message 对外展示的固定提示
 */
public record SectionResult<T>(String status, T data, String message) {
    /**
     * 查询故障只记录本分项状态，供任务节点与独立读取用例使用。
     *
     * @param <T> 当前业务数据类型
     * @param query 执行分项读取的函数
     * @param empty 判断业务数据是否为空的规则或判断结果
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static <T> SectionResult<T> attempt(Supplier<T> query, Predicate<T> empty) {
        try {
            T data = query.get();
            return success(data, empty.test(data));
        } catch (RuntimeException error) {
            org.apache.commons.logging.LogFactory.getLog(SectionResult.class).warn("商详分项读取失败", error);
            return unavailable("FAILED", "暂时不可用，请稍后重试");
        }
    }
    /**
     * 创建成功或成功但无数据的区域响应。
     *
     * @param <T> 当前业务数据类型
     * @param data 业务数据
     * @param empty 判断业务数据是否为空的规则或判断结果
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static <T> SectionResult<T> success(T data, boolean empty) {
        return new SectionResult<>(empty ? "EMPTY" : "SUCCESS", data, null);
    }
    /**
     * 创建没有伪造业务数据的不可用区域响应。
     *
     * @param <T> 当前业务数据类型
     * @param status 业务或节点状态
     * @param message 对外展示的固定提示
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static <T> SectionResult<T> unavailable(String status, String message) {
        return new SectionResult<>(status, null, message);
    }
    /**
     * 判断区域数据是否可以展示，包含成功、空数据及部分来源成功。
     *
     * @return 是否存在可以展示的分项结果
     */
    public boolean available() { return status.equals("SUCCESS") || status.equals("EMPTY") || status.equals("PARTIAL"); }
    /**
     * 保留区域状态及提示，只转换非空业务数据。
     *
     * @param <R> 转换后的业务数据类型
     * @param mapper 数据映射器
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public <R> SectionResult<R> map(Function<T, R> mapper) {
        return new SectionResult<>(status, data == null ? null : mapper.apply(data), message);
    }
}
