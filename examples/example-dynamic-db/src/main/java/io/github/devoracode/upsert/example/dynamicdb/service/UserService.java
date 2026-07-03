package io.github.devoracode.upsert.example.dynamicdb.service;

import io.github.devoracode.upsert.example.dynamicdb.entity.User;
import org.apache.ibatis.executor.BatchResult;

import java.util.Collection;
import java.util.List;

public interface UserService {

    int upsert(User user);

    int upsertBatch(List<User> users);

    List<BatchResult> upsertBatchWithResult(Collection<User> users);

    List<BatchResult> upsertBatchWithResult(Collection<User> users, int batchSize);

    User findByEmail(String email);

    List<User> findAll();

    int deleteAll();
}