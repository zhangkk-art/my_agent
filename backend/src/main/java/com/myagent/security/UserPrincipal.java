package com.myagent.security;

public class UserPrincipal {

    private final String userId;
    private final String username;

    public UserPrincipal(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
}
