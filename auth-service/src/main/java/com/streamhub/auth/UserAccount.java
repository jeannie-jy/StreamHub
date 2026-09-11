package com.streamhub.auth;

public record UserAccount(long id, String username, String passwordHash, String role, String status) {
}
