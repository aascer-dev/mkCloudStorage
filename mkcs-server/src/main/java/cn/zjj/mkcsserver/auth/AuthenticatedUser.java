package cn.zjj.mkcsserver.auth;

public record AuthenticatedUser(Long userId, String username, Long tokenVersion) {
}
