package cn.zjj.mkcsserver.auth;

public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
