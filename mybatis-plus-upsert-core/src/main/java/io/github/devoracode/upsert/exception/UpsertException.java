package io.github.devoracode.upsert.exception;

/**
 * mybatis-plus-upsert 库抛出的运行时异常。
 * 涵盖配置错误、方言创建失败以及 SQL 生成问题。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertException extends RuntimeException {

    /**
     * 使用指定的详情消息构造一个 UpsertException。
     *
     * @param message 详情消息
     */
    public UpsertException(String message) {
        super(message);
    }

    /**
     * 使用指定的详情消息和原因构造一个 UpsertException。
     *
     * @param message 详情消息
     * @param cause   此异常的根本原因
     */
    public UpsertException(String message, Throwable cause) {
        super(message, cause);
    }
}
