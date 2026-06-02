package com.myagent.service;

import com.myagent.mapper.UserMapper;
import com.myagent.model.User;
import com.myagent.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public String register(String username, String password) {
        if (username == null || username.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少 2 个字符");
        }
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 6 个字符");
        }
        if (userMapper.findByUsername(username.trim()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    public String login(String username, String password) {
        if (username == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        User user = userMapper.findByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }
}
