package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.dto.OAuth2UserInfo;
import cn.zjj.mkcsmodel.dto.RegisterRequest;
import cn.zjj.mkcsmodel.entity.OauthIdentities;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.auth.TokenService;
import cn.zjj.mkcsserver.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OAuth2 服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements OAuth2Service {

    private final UsersService usersService;
    private final OauthIdentitiesService oauthIdentitiesService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserRolesService userRolesService;
    private final TokenService tokenService;

    @Value("${oauth2.github.client-id:}")
    private String githubClientId;

    @Value("${oauth2.github.client-secret:}")
    private String githubClientSecret;

    @Value("${oauth2.github.redirect-uri:}")
    private String githubRedirectUri;

    private static final String GITHUB_AUTHORIZE_URL =
        "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL =
        "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_API_URL =
        "https://api.github.com/user";
    private static final String GITHUB_USER_EMAIL_API_URL =
        "https://api.github.com/user/emails";

    @Override
    public String getGitHubAuthorizationUrl(String state) {
        Assert.hasText(githubClientId, "GitHub Client ID 未配置，请联系管理员");
        Assert.hasText(githubRedirectUri, "GitHub Redirect URI 未配置，请联系管理员");

        // 如果未提供 state，则生成并存储
        if (state == null || state.trim().isEmpty()) {
            state = generateAndStoreState();
        }

        return String.format(
            "%s?client_id=%s&redirect_uri=%s&scope=user:email&state=%s",
            GITHUB_AUTHORIZE_URL,
            githubClientId,
            githubRedirectUri,
            state
        );
    }

    /**
     * 生成并存储 state 参数用于 CSRF 保护
     */
    private String generateAndStoreState() {
        String state = UUID.randomUUID().toString();
        String key = "oauth2:state:" + state;

        // 将 state 存储到 Redis，有效期 10 分钟
        redisTemplate
            .opsForValue()
            .set(key, System.currentTimeMillis(), 10, TimeUnit.MINUTES);

        log.info("生成并存储 OAuth2 state: {}", state);
        return state;
    }

    /**
     * 验证并删除 state 参数
     */
    private boolean validateAndRemoveState(String state) {
        if (state == null || state.trim().isEmpty()) {
            log.warn("State 参数为空");
            return false;
        }

        String key = "oauth2:state:" + state;
        Long createdAt = (Long) redisTemplate.opsForValue().get(key);

        if (createdAt == null) {
            log.warn("Invalid or expired state parameter: {}", state);
            return false;
        }

        // 删除 state 以防止重用
        redisTemplate.delete(key);
        log.info("State 验证成功并已删除: {}", state);
        return true;
    }

    /**
     * 获取Github的回调
     * @param code 授权码
     * @param rememberMe 是否记住登录状态
     * @return 响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> handleGitHubCallback(
        String code,
        Boolean rememberMe
    ) {
        Result<?> result = handleGitHubCallback(code, null, rememberMe);
        // 此方法用于向后兼容，假设不验证 state
        if (result.getData() instanceof LoginResponse) {
            @SuppressWarnings("unchecked")
            Result<LoginResponse> loginResult = (Result<LoginResponse>) result;
            return loginResult;
        }
        // 如果需要注册，则返回错误
        return Result.error("需要完成注册流程");
    }

    /**
     * 处理 GitHub OAuth2 回调并验证 state
     */
    @Override
    public Result<?> handleGitHubCallback(
        String code,
        String state,
        Boolean rememberMe
    ) {
        Assert.hasText(code, "授权码不能为空");

        // 如果提供了 state 参数，则进行验证（CSRF 保护）
        if (state != null && !state.trim().isEmpty()) {
            if (!validateAndRemoveState(state)) {
                log.error("非法state参数，可能是CSRF攻击: {}", state);
                return Result.error(ResultCode.OAUTH_STATE_INVALID);
            }
        }

        // 1. 获取 GitHub 用户信息
        OAuth2UserInfo userInfo = getGitHubUserInfo(code);
        Assert.notNull(userInfo, "获取 GitHub 用户信息失败");
        Assert.hasText(userInfo.getIdentifier(), "GitHub 用户标识为空");

        // 2. 查询是否已存在 OAuth 关联
        OauthIdentities oauthIdentity =
            oauthIdentitiesService.getByProviderAndIdentifier(
                "github",
                userInfo.getIdentifier()
            );

        Users user;
        if (oauthIdentity != null) {
            // 已关联，直接登录
            user = usersService.getById(oauthIdentity.getUserId());
            Assert.notNull(user, ResultCode.USER_NOT_FOUND);
            Assert.isTrue(user.getStatus() == 1, ResultCode.USER_DISABLED);

            // 更新 OAuth 凭证
            oauthIdentitiesService.update(
                user.getId(),
                "github",
                userInfo.getIdentifier(),
                userInfo.getAccessToken()
            );

            log.info(
                "GitHub OAuth2 登录成功: userId={}, githubId={}",
                user.getId(),
                userInfo.getIdentifier()
            );

            return Result.success("GitHub 登录成功", usersService.issueLoginResponse(user));
        } else {
            // 未关联，需要注册新用户 - 存储临时数据
            String tempUserId = storeTempOAuthData(userInfo);

            // 返回需要注册的响应
            Map<String, Object> response = new HashMap<>();
            response.put("requiresRegistration", true);
            response.put("tempUserId", tempUserId);
            response.put(
                "suggestedEmail",
                userInfo.getEmail() != null ? userInfo.getEmail() : ""
            );
            response.put("suggestedUsername", userInfo.getUsername());
            response.put(
                "hasGitHubEmail",
                userInfo.getEmail() != null &&
                    !userInfo.getEmail().trim().isEmpty()
            );

            log.info(
                "GitHub OAuth2 新用户，需要完成注册: tempUserId={}, githubId={}",
                tempUserId,
                userInfo.getIdentifier()
            );
            return Result.success("需要完成注册", response);
        }
    }

    @Override
    public OAuth2UserInfo getGitHubUserInfo(String code) {
        Assert.hasText(code, "授权码不能为空");
        Assert.hasText(githubClientId, "GitHub Client ID 未配置");
        Assert.hasText(githubClientSecret, "GitHub Client Secret 未配置");

        try {
            // 1. 使用授权码换取访问令牌
            String accessToken = exchangeCodeForToken(code);
            Assert.hasText(accessToken, "获取 GitHub Access Token 失败");

            // 2. 使用访问令牌获取用户信息

            String userJson = webClient
                .get()
                .uri(GITHUB_USER_API_URL)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            Assert.hasText(userJson, "获取 GitHub 用户信息失败");

            JsonNode userNode = objectMapper.readTree(userJson);

            // 3. 解析用户信息
            OAuth2UserInfo userInfo = new OAuth2UserInfo();
            userInfo.setIdentifier(userNode.get("id").asText());
            userInfo.setUsername(
                userNode.has("login") ? userNode.get("login").asText() : null
            );
            userInfo.setNickname(
                userNode.has("name") && !userNode.get("name").isNull()
                    ? userNode.get("name").asText()
                    : userNode.get("login").asText()
            );
            userInfo.setAvatarUrl(
                userNode.has("avatar_url")
                    ? userNode.get("avatar_url").asText()
                    : null
            );
            userInfo.setAccessToken(accessToken);

            // 4. 获取邮箱（GitHub 用户信息中的 email 可能为 null）
            if (userNode.has("email") && !userNode.get("email").isNull()) {
                userInfo.setEmail(userNode.get("email").asText());
            } else {
                // 尝试从邮箱 API 获取
                String emailJson = webClient
                    .get()
                    .uri(GITHUB_USER_EMAIL_API_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                if (emailJson != null) {
                    JsonNode emailsNode = objectMapper.readTree(emailJson);
                    if (emailsNode.isArray() && emailsNode.size() > 0) {
                        // 查找主邮箱或第一个已验证的邮箱
                        for (JsonNode emailNode : emailsNode) {
                            if (
                                emailNode.get("primary").asBoolean() &&
                                emailNode.get("verified").asBoolean()
                            ) {
                                userInfo.setEmail(
                                    emailNode.get("email").asText()
                                );
                                break;
                            }
                        }
                        // 如果没有主邮箱，使用第一个已验证的邮箱
                        if (userInfo.getEmail() == null) {
                            for (JsonNode emailNode : emailsNode) {
                                if (emailNode.get("verified").asBoolean()) {
                                    userInfo.setEmail(
                                        emailNode.get("email").asText()
                                    );
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            log.info(
                "获取 GitHub 用户信息成功: githubId={}, username={}",
                userInfo.getIdentifier(),
                userInfo.getUsername()
            );
            return userInfo;
        } catch (Exception e) {
            log.error("获取 GitHub 用户信息失败", e);
            throw new RuntimeException(
                "获取 GitHub 用户信息失败: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 使用授权码换取访问令牌
     */
    private String exchangeCodeForToken(String code) {
        try {
            String requestBody = String.format(
                "client_id=%s&client_secret=%s&code=%s&redirect_uri=%s",
                githubClientId,
                githubClientSecret,
                code,
                githubRedirectUri
            );

            String response = webClient
                .post()
                .uri(GITHUB_TOKEN_URL)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            Assert.hasText(response, "GitHub Token 响应为空");

            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode.has("error")) {
                String error = jsonNode.get("error").asText();
                String errorDescription = jsonNode.has("error_description")
                    ? jsonNode.get("error_description").asText()
                    : "";
                throw new RuntimeException(
                    "GitHub Token 错误: " + error + " - " + errorDescription
                );
            }

            String accessToken = jsonNode.get("access_token").asText();
            log.info("获取 GitHub Access Token 成功");
            return accessToken;
        } catch (Exception e) {
            log.error("获取 GitHub Access Token 失败", e);
            throw new RuntimeException(
                "获取 GitHub Access Token 失败: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 将临时 OAuth 数据存储到 Redis
     */
    private String storeTempOAuthData(OAuth2UserInfo userInfo) {
        String tempUserId = UUID.randomUUID().toString();
        String key = "oauth2:temp:" + tempUserId;

        Map<String, String> data = new HashMap<>();
        data.put("provider", "github");
        data.put("identifier", userInfo.getIdentifier());
        data.put("username", userInfo.getUsername());
        data.put("nickname", userInfo.getNickname());
        data.put(
            "email",
            userInfo.getEmail() != null ? userInfo.getEmail() : ""
        );
        data.put(
            "emailVerified",
            userInfo.getEmail() != null ? "true" : "false"
        ); // GitHub 邮箱已验证
        data.put(
            "avatarUrl",
            userInfo.getAvatarUrl() != null ? userInfo.getAvatarUrl() : ""
        );
        data.put("accessToken", userInfo.getAccessToken());

        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);

        log.info(
            "存储临时 OAuth 数据: tempUserId={}, email={}",
            tempUserId,
            userInfo.getEmail()
        );
        return tempUserId;
    }

    /**
     * 从 Redis 获取临时 OAuth 数据
     */
    private Map<String, String> getTempOAuthData(String tempUserId) {
        String key = "oauth2:temp:" + tempUserId;
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(key);

        if (rawData.isEmpty()) {
            throw new RuntimeException("OAuth2 临时数据已过期，请重新登录");
        }

        Map<String, String> data = new HashMap<>();
        rawData.forEach((k, v) ->
            data.put(k.toString(), v != null ? v.toString() : "")
        );

        return data;
    }

    /**
     * 发送邮箱验证码
     */
    @Override
    public Result<Void> sendVerificationCode(String tempUserId, String email) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");
        Assert.hasText(email, "邮箱不能为空");

        // 验证邮箱格式
        if (!isValidEmail(email)) {
            return Result.error(ResultCode.EMAIL_FORMAT_INVALID);
        }

        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(999999));

        // 存储到 Redis
        String key = "oauth2:verify:" + tempUserId;
        Map<String, String> verifyData = new HashMap<>();
        verifyData.put("code", code);
        verifyData.put("email", email);
        verifyData.put("createdAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(key, verifyData);
        redisTemplate.expire(key, 10, TimeUnit.MINUTES);

        // 发送邮件
        try {
            emailService.sendVerificationCode(email, code, "OAuth2注册");
            log.info(
                "发送邮箱验证码成功: tempUserId={}, email={}",
                tempUserId,
                email
            );
            return Result.success("验证码已发送", null);
        } catch (Exception e) {
            log.error("发送邮箱验证码失败", e);
            return Result.error(ResultCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * 使用验证码验证邮箱
     */
    @Override
    public Result<Void> verifyEmailWithCode(
        String tempUserId,
        String email,
        String code
    ) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");
        Assert.hasText(email, "邮箱不能为空");
        Assert.hasText(code, "验证码不能为空");

        // 获取存储的验证数据
        String key = "oauth2:verify:" + tempUserId;
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(key);

        if (rawData.isEmpty()) {
            return Result.error(ResultCode.VERIFICATION_CODE_EXPIRED);
        }

        String storedCode = (String) rawData.get("code");
        String storedEmail = (String) rawData.get("email");

        // 验证验证码和邮箱是否匹配
        if (!code.equals(storedCode) || !email.equals(storedEmail)) {
            return Result.error(ResultCode.VERIFICATION_CODE_INVALID);
        }

        // 更新临时数据，标记邮箱已验证
        String tempKey = "oauth2:temp:" + tempUserId;
        redisTemplate.opsForHash().put(tempKey, "email", email);
        redisTemplate.opsForHash().put(tempKey, "emailVerified", "true");

        // 删除验证码
        redisTemplate.delete(key);

        log.info("邮箱验证成功: tempUserId={}, email={}", tempUserId, email);
        return Result.success("邮箱验证成功", null);
    }

    /**
     * 验证 GitHub 邮箱（无需验证码）
     */
    public Result<Void> verifyGitHubEmail(String tempUserId) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");

        // 获取临时数据
        Map<String, String> tempData = getTempOAuthData(tempUserId);
        String email = tempData.get("email");

        if (email == null || email.trim().isEmpty()) {
            return Result.error(ResultCode.EMAIL_NOT_PROVIDED);
        }

        // GitHub 邮箱已验证，只需标记为已确认
        String tempKey = "oauth2:temp:" + tempUserId;
        redisTemplate.opsForHash().put(tempKey, "emailVerified", "true");

        log.info(
            "确认 GitHub 邮箱: tempUserId={}, email={}",
            tempUserId,
            email
        );
        return Result.success("邮箱确认成功", null);
    }

    /**
     * 完成 OAuth2 注册
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> completeOAuth2Registration(
        String tempUserId,
        String username,
        Boolean rememberMe
    ) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");
        Assert.hasText(username, "用户名不能为空");

        // 1. 获取临时 OAuth 数据
        Map<String, String> oauthData = getTempOAuthData(tempUserId);

        // 2. 验证邮箱是否已验证
        String emailVerified = oauthData.get("emailVerified");
        if (!"true".equals(emailVerified)) {
            return Result.error(ResultCode.EMAIL_NOT_VERIFIED);
        }

        String email = oauthData.get("email");
        Assert.hasText(email, "邮箱不能为空");

        // 3. 验证用户名
        if (!usersService.isUsernameAvailable(username, null)) {
            return Result.error(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // 4. 检查邮箱冲突
        if (!usersService.isEmailAvailable(email, null)) {
            // 邮箱已存在 - 可能需要合并账号
            return Result.error(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 5. 生成加密的默认密码
        String defaultPassword = generateSecurePassword();
        String encryptedPassword = passwordEncoder.encode(defaultPassword);

        // 6. 创建用户
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword(encryptedPassword); // Already encrypted
        registerRequest.setEmail(email);
        registerRequest.setNickname(oauthData.get("nickname"));
        registerRequest.setRememberMe(false); // 已加密

        Result<LoginResponse> registerResult = usersService.register(
            registerRequest
        );
        if (registerResult.getCode() != 200) {
            return registerResult;
        }

        // 7. 获取创建的用户
        Users user = usersService.getUserByUsername(username);
        Assert.notNull(user, "注册后获取用户失败");

        // 8. 更新头像
        String avatarUrl = oauthData.get("avatarUrl");
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            user.setAvatarUrl(avatarUrl);
            usersService.updateById(user);
        }

        // 9. 创建 OAuth 身份
        oauthIdentitiesService.create(
            user.getId(),
            oauthData.get("provider"),
            oauthData.get("identifier"),
            oauthData.get("accessToken")
        );

        // 10. 清理临时数据
        redisTemplate.delete("oauth2:temp:" + tempUserId);
        redisTemplate.delete("oauth2:verify:" + tempUserId);

        log.info(
            "OAuth2 注册完成: userId={}, username={}, email={}",
            user.getId(),
            username,
            email
        );
        return Result.success("注册并登录成功", usersService.issueLoginResponse(user));
    }

    /**
     * 启动账号合并
     */
    public Result<Map<String, Object>> initiateAccountMerge(
        String tempUserId,
        String email
    ) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");
        Assert.hasText(email, "邮箱不能为空");

        // 1. 检查邮箱是否存在
        Users existingUser = usersService.getUserByEmail(email);
        if (existingUser == null) {
            return Result.error(ResultCode.USER_NOT_FOUND);
        }

        // 2. 返回合并选项
        Map<String, Object> response = new HashMap<>();
        response.put("requiresMerge", true);
        response.put("existingUserId", existingUser.getId());
        response.put("existingUsername", existingUser.getUsername());
        response.put(
            "message",
            "检测到该邮箱已注册，请通过账号恢复流程验证身份后合并账号"
        );

        log.info(
            "检测到账号合并需求: tempUserId={}, email={}, existingUserId={}",
            tempUserId,
            email,
            existingUser.getId()
        );
        return Result.success("需要账号合并", response);
    }

    /**
     * 完成账号合并
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> completeAccountMerge(
        String tempUserId,
        Long existingUserId,
        Boolean rememberMe
    ) {
        Assert.hasText(tempUserId, "临时用户ID不能为空");
        Assert.notNull(existingUserId, "现有用户ID不能为空");

        // 1. 获取临时 OAuth 数据
        Map<String, String> oauthData = getTempOAuthData(tempUserId);

        // 2. 验证现有用户
        Users existingUser = usersService.getById(existingUserId);
        Assert.notNull(existingUser, ResultCode.USER_NOT_FOUND);
        Assert.isTrue(existingUser.getStatus() == 1, ResultCode.USER_DISABLED);

        // 3. 检查 OAuth 身份是否已关联到其他用户
        OauthIdentities existingIdentity =
            oauthIdentitiesService.getByProviderAndIdentifier(
                oauthData.get("provider"),
                oauthData.get("identifier")
            );

        if (
            existingIdentity != null &&
            !existingIdentity.getUserId().equals(existingUserId)
        ) {
            return Result.error(ResultCode.OAUTH_ALREADY_LINKED);
        }

        // 4. 检查 OAuth 身份是否存在，决定创建或更新
        OauthIdentities existingOAuth =
            oauthIdentitiesService.getByProviderAndIdentifier(
                oauthData.get("provider"),
                oauthData.get("identifier")
            );

        if (existingOAuth != null) {
            // 更新现有 OAuth 身份
            oauthIdentitiesService.update(
                existingUserId,
                oauthData.get("provider"),
                oauthData.get("identifier"),
                oauthData.get("accessToken")
            );
        } else {
            // 创建新 OAuth 身份
            oauthIdentitiesService.create(
                existingUserId,
                oauthData.get("provider"),
                oauthData.get("identifier"),
                oauthData.get("accessToken")
            );
        }

        // 5. 如果用户未设置自定义头像，则更新头像
        String avatarUrl = oauthData.get("avatarUrl");
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            // 仅在当前头像为默认或空时更新
            if (
                existingUser.getAvatarUrl() == null ||
                existingUser.getAvatarUrl().trim().isEmpty()
            ) {
                existingUser.setAvatarUrl(avatarUrl);
                usersService.updateById(existingUser);
                log.info(
                    "更新合并账号头像: userId={}, avatarUrl={}",
                    existingUserId,
                    avatarUrl
                );
            }
        }

        // 6. 清理临时数据
        redisTemplate.delete("oauth2:temp:" + tempUserId);
        redisTemplate.delete("oauth2:verify:" + tempUserId);

        log.info(
            "账号合并成功: userId={}, provider={}, identifier={}",
            existingUserId,
            oauthData.get("provider"),
            oauthData.get("identifier")
        );
        return Result.success("账号合并成功", usersService.issueLoginResponse(existingUser));
    }

    /**
     * 生成安全的随机密码
     */
    private String generateSecurePassword() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email != null && email.matches(emailRegex);
    }

    /**
     * 如果头像已更改则更新
     */
    private void updateAvatarIfChanged(Users user, String newAvatarUrl) {
        if (newAvatarUrl != null && !newAvatarUrl.trim().isEmpty()) {
            if (!newAvatarUrl.equals(user.getAvatarUrl())) {
                user.setAvatarUrl(newAvatarUrl);
                usersService.updateById(user);
                log.info(
                    "更新用户头像: userId={}, avatarUrl={}",
                    user.getId(),
                    newAvatarUrl
                );
            }
        }
    }

    /**
     * 解除 OAuth2 绑定
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbindOAuth(Long userId, String provider) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.hasText(provider, "平台标识不能为空");

        // 1. 验证用户存在
        Users user = usersService.getById(userId);
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 2. 查询 OAuth 绑定
        OauthIdentities oauthIdentity =
            oauthIdentitiesService.getByProviderAndUserId(provider, userId);
        if (oauthIdentity == null) {
            return Result.error("未找到该平台的绑定记录");
        }

        // 3. 删除绑定（所有 OAuth 用户都有默认密码，可以直接解绑）
        boolean removed = oauthIdentitiesService.removeById(
            oauthIdentity.getId()
        );
        if (!removed) {
            return Result.error("解绑失败");
        }

        log.info(
            "OAuth2 解绑成功: userId={}, provider={}, identifier={}",
            userId,
            provider,
            oauthIdentity.getIdentifier()
        );

        return Result.success("解绑成功", null);
    }

    /**
     * 获取用户的 OAuth2 绑定列表
     */
    @Override
    public Result<Map<String, Object>> getUserOAuthBindings(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");

        // 1. 验证用户存在
        Users user = usersService.getById(userId);
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 2. 查询所有 OAuth 绑定
        List<OauthIdentities> bindings = oauthIdentitiesService.listByUserId(
            userId
        );

        // 3. 构建响应数据
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put(
            "hasPassword",
            user.getPassword() != null && !user.getPassword().trim().isEmpty()
        );

        List<Map<String, Object>> bindingList = new ArrayList<>();
        for (OauthIdentities binding : bindings) {
            Map<String, Object> bindingInfo = new HashMap<>();
            bindingInfo.put("provider", binding.getProvider());
            bindingInfo.put("identifier", binding.getIdentifier());
            bindingInfo.put("createdAt", binding.getCreatedAt());
            // 所有绑定都可以解绑（因为 OAuth 用户都有默认密码）
            bindingInfo.put("canUnbind", true);
            bindingList.add(bindingInfo);
        }

        data.put("bindings", bindingList);
        data.put("totalBindings", bindingList.size());

        return Result.success("获取绑定列表成功", data);
    }
}
