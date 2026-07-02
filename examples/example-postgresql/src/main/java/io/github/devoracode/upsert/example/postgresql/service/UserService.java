package io.github.devoracode.upsert.example.postgresql.service;

import io.github.devoracode.upsert.example.postgresql.entity.User;

import java.util.List;

public interface UserService {

    User upsert(User user);

    List<User> upsertBatch(List<User> users);

    User findByEmail(String email);

    List<User> findAll();

    void deleteAll();
}