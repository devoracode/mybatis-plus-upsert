package io.github.devoracode.upsert.example.dynamicdbsb3.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import io.github.devoracode.upsert.example.dynamicdbsb3.entity.User;
import io.github.devoracode.upsert.example.dynamicdbsb3.mapper.UserMapper;
import io.github.devoracode.upsert.example.dynamicdbsb3.service.UserService;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @DS("mysql")
    @Transactional
    public int upsert(User user) {
        return userMapper.upsert(user);
    }

    @Override
    @DS("postgresql")
    @Transactional
    public int upsertBatch(List<User> users) {
        return userMapper.upsertBatch(users);
    }

    @Override
    @DS("mysql")
    @Transactional
    public List<BatchResult> upsertBatchWithResult(Collection<User> users) {
        return userMapper.upsert(users);
    }

    @Override
    @DS("postgresql")
    @Transactional
    public List<BatchResult> upsertBatchWithResult(Collection<User> users, int batchSize) {
        return userMapper.upsert(users, batchSize);
    }

    @Override
    @DS("mysql")
    public User findByEmail(String email) {
        return userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)
        ).stream().findFirst().orElse(null);
    }

    @Override
    @DS("postgresql")
    public List<User> findAll() {
        return userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
    }

    @Override
    @DS("mysql")
    @Transactional
    public int deleteAll() {
        return userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
    }
}
