package io.github.devoracode.upsert.example.postgresql.service.impl;

import io.github.devoracode.upsert.example.postgresql.entity.User;
import io.github.devoracode.upsert.example.postgresql.mapper.UserMapper;
import io.github.devoracode.upsert.example.postgresql.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public User upsert(User user) {
        userMapper.upsert(user);
        return userMapper.selectById(user.getId());
    }

    @Override
    @Transactional
    public List<User> upsertBatch(List<User> users) {
        userMapper.upsertBatch(users);
        return userMapper.selectList(null);
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.selectList(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.<User>query()
                        .eq(User::getUsername, username))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(null);
    }

    @Override
    @Transactional
    public void deleteAll() {
        userMapper.delete(null);
    }
}