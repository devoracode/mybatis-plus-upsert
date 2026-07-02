package io.github.devoracode.upsert.example.mysql.service;

import io.github.devoracode.upsert.example.mysql.entity.User;

import java.util.List;

public interface UserService {

    User upsert(User user);

    List<User> upsertBatch(List<User> users);

    User findByUsername(String username);

    List<User> findAll();

    void deleteAll();
}