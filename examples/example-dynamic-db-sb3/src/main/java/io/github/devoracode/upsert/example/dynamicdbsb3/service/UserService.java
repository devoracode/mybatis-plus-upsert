package io.github.devoracode.upsert.example.dynamicdbsb3.service;

import io.github.devoracode.upsert.example.dynamicdbsb3.entity.User;
import org.apache.ibatis.executor.BatchResult;

import java.util.Collection;
import java.util.List;

public interface UserService {

    int upsert(User user);

    /**
     * 批量 Upsert：复用单行语句，由 MyBatis-Plus 的 {@code MybatisBatch} 在
     * {@code ExecutorType.BATCH} 下逐条提交，返回各批次的 {@link BatchResult}。
     */
    List<BatchResult> upsertBatch(Collection<User> users);

    List<BatchResult> upsertBatch(Collection<User> users, int batchSize);

    User findByEmail(String email);

    List<User> findAll();

    int deleteAll();
}
