package io.github.devoracode.upsert.exception;

/**
 * mybatis-plus-upsert 库抛出的运行时异常。
 * 涵盖配置错误、方言创建失败以及 SQL 生成问题。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertException extends RuntimeException {

    public UpsertException(String message) {
        super(message);
    }

    public UpsertException(String message, Throwable cause) {
        super(message, cause);
    }
}
