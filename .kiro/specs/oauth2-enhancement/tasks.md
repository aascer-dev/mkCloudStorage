# Implementation Plan: OAuth2 Enhancement (Simplified)

## Overview

This simplified implementation plan enhances the existing OAuth2ServiceImpl without creating new database tables or many new services. The focus is on:

1. Adding state parameter validation (CSRF protection)
2. Implementing email binding flow (optional/skippable)
3. Adding username selection during registration
4. Implementing account merging logic
5. Generating encrypted default passwords

**No database changes required** - uses existing Users and OauthIdentities tables.

## Tasks

### Phase 1: Core OAuth2 Enhancements

- [x] 1. Add state parameter validation to OAuth2ServiceImpl
  - Add RedisTemplate dependency injection
  - Implement `generateAndStoreState()` method
    - Generate UUID state token
    - Store in Redis with key `oauth2:state:{state}` and 10-minute TTL
  - Implement `validateAndRemoveState(String state)` method
    - Check if state exists in Redis
    - Delete state after validation (prevent reuse)
    - Log security warning if invalid
  - Update `getGitHubAuthorizationUrl()` to generate and include state
  - Update `handleGitHubCallback()` to validate state parameter
  - Add error handling for invalid/expired state
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 2. Implement temporary OAuth data storage and email verification
  - Implement `storeTempOAuthData(OAuth2UserInfo userInfo)` method
    - Generate UUID tempUserId
    - Store OAuth data in Redis hash with key `oauth2:temp:{tempUserId}`
    - Include `emailVerified` field (true if GitHub email, false otherwise)
    - Set 30-minute TTL
    - Return tempUserId
  - Implement `getTempOAuthData(String tempUserId)` method
    - Retrieve data from Redis
    - Throw exception if expired
  - Implement `sendVerificationCode(String tempUserId, String email)` method
    - Validate email format
    - Generate 6-digit random code
    - Store code in Redis with key `oauth2:verify:{tempUserId}` (10-minute TTL)
    - Send email via existing email service
    - Return success/error result
  - Implement `verifyEmailWithCode(String tempUserId, String email, String code)` method
    - Retrieve verification data from Redis
    - Validate code matches and email matches
    - Update temp data to mark email as verified
    - Delete verification code from Redis
    - Return success/error result
  - Implement `verifyGitHubEmail(String tempUserId)` method
    - Get temp data and verify GitHub email exists
    - Mark email as verified in temp data
    - Return success result
  - Add helper method `generateSecurePassword()` for default passwords
  - Add helper method `isValidEmail(String email)` for email validation
  - Add helper method `updateAvatarIfChanged(Users user, String avatarUrl)`
  - _Requirements: 1.3, 1.4, 1.6, 1.7, 2.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

- [x] 3. Refactor handleGitHubCallback for new registration flow
  - Modify method signature to accept `state` parameter
  - Add state validation at the beginning
  - Keep existing user login logic unchanged
  - For new users:
    - Call `storeTempOAuthData()` instead of immediate registration
    - Return response with `requiresRegistration: true` and `tempUserId`
    - Include `suggestedEmail` and `suggestedUsername` in response
  - Remove old auto-registration logic (`registerUserFromOAuth`)
  - _Requirements: 1.1, 1.3, 3.3, 11.2_

### Phase 2: Registration Completion

- [x] 4. Implement completeOAuth2Registration method
  - Add method signature: `completeOAuth2Registration(String tempUserId, String username, Boolean rememberMe)`
  - Retrieve temp OAuth data from Redis
  - Verify email has been verified (check `emailVerified` field)
  - Get email from temp data
  - Validate username availability
  - Check email conflict (return error if exists)
  - Generate and encrypt default password using BCrypt
  - Create user via existing UsersService.register()
  - Update user avatar from OAuth data
  - Create OAuth identity via OauthIdentitiesService
  - Clean up temp data and verification code from Redis
  - Perform login with StpUtil
  - Return LoginResponse
  - _Requirements: 1.8, 1.9, 1.11, 2.1, 2.2, 11.4, 11.5, 11.7_

- [x] 5. Add username validation helper methods
  - Leverage existing `usersService.isUsernameAvailable()`
  - Add format validation (3-32 chars, alphanumeric + underscore/hyphen)
  - Return clear error messages for invalid usernames
  - _Requirements: 11.4, 11.5, 11.6_

### Phase 3: Account Merging

- [x] 6. Implement account merge detection
  - Add method `initiateAccountMerge(String tempUserId, String email)`
  - Check if email exists using `usersService.getUserByEmail()`
  - Return response with merge options:
    - `requiresMerge: true`
    - `existingUserId`
    - `existingUsername`
    - Message prompting account recovery
  - _Requirements: 5.1, 5.2, 8.1, 8.2_

- [x] 7. Implement account merge completion
  - Add method `completeAccountMerge(String tempUserId, Long existingUserId, Boolean rememberMe)`
  - Retrieve temp OAuth data
  - Verify existing user exists and is active
  - Check OAuth identity not already linked to different user
  - Create OAuth identity linking to existing user
  - Update avatar only if existing user has no custom avatar
  - Clean up temp data
  - Perform login
  - Return LoginResponse
  - _Requirements: 5.5, 5.6, 5.7, 8.3, 8.4, 8.5, 8.6_

### Phase 4: Controller Endpoints

- [x] 8. Update OAuth2Controller
  - Update `getGitHubAuthorizationUrl()` endpoint
    - Remove state parameter from request (service generates it)
  - Update `handleGitHubCallback()` endpoint
    - Add `@RequestParam String state` parameter
    - Pass state to service method
  - Add new endpoint `POST /oauth2/send-verification-code`
    - Parameters: tempUserId, email
    - Call `oauth2Service.sendVerificationCode()`
  - Add new endpoint `POST /oauth2/verify-email`
    - Parameters: tempUserId, email, code (optional), isGitHubEmail (optional)
    - If isGitHubEmail=true, call `verifyGitHubEmail()`
    - Otherwise, call `verifyEmailWithCode()`
  - Add new endpoint `POST /oauth2/complete-registration`
    - Parameters: tempUserId, username, rememberMe
    - Call `oauth2Service.completeOAuth2Registration()`
  - Add new endpoint `POST /oauth2/initiate-merge`
    - Parameters: tempUserId, email
    - Call `oauth2Service.initiateAccountMerge()`
  - Add new endpoint `POST /oauth2/complete-merge`
    - Parameters: tempUserId, existingUserId, rememberMe
    - Call `oauth2Service.completeAccountMerge()`
  - Add proper error handling and validation
  - _Requirements: 1.1, 1.4, 1.6, 1.7, 1.10, 8.3, 13.2, 13.4_

### Phase 5: Error Handling

- [x] 9. Add new error codes to ResultCode enum
  - Add `OAUTH_STATE_INVALID(4001, "OAuth2 state 参数无效")`
  - Add `OAUTH_TEMP_DATA_EXPIRED(4002, "OAuth2 临时数据已过期，请重新登录")`
  - Add `OAUTH_ALREADY_LINKED(4003, "该 OAuth 账号已绑定到其他用户")`
  - Add `EMAIL_FORMAT_INVALID(4004, "邮箱格式不正确")`
  - Add `EMAIL_NOT_VERIFIED(4005, "邮箱未验证")`
  - Add `EMAIL_NOT_PROVIDED(4006, "GitHub 未提供邮箱")`
  - Add `VERIFICATION_CODE_EXPIRED(4007, "验证码已过期")`
  - Add `VERIFICATION_CODE_INVALID(4008, "验证码不正确")`
  - Add `EMAIL_SEND_FAILED(4009, "验证码发送失败")`
  - _Requirements: 3.4, 5.4, 13.5, 13.6_

- [x] 10. Add comprehensive error handling
  - Handle Redis connection failures gracefully
  - Handle expired temp data with clear error messages
  - Handle OAuth provider API failures
  - Add logging for all error scenarios
  - _Requirements: 3.4, 3.6_

### Phase 6: Frontend Integration

- [ ] 11. Create email binding page component (MANDATORY)
  - URL: `/oauth2/email-binding?tempUserId={tempUserId}`
  - **Case 1: GitHub provides verified email**
    - Display: "GitHub 已验证邮箱: user@example.com"
    - Show "确认使用此邮箱" button
    - Show "使用其他邮箱" link
    - On "确认": Call `/oauth2/verify-email?isGitHubEmail=true`
    - On "使用其他邮箱": Show email input field
  - **Case 2: Custom email input**
    - Email input field with validation
    - "发送验证码" button
    - After sending: Show 6-digit code input
    - Show "重新发送" link (60-second cooldown)
    - "验证" button to submit code
    - Call `/oauth2/send-verification-code` then `/oauth2/verify-email`
  - **Conflict handling:**
    - Show dialog: "该邮箱已注册，是否为您的现有账号？"
    - "是" → Redirect to account recovery
    - "否" → Clear input, ask for different email
  - **NO skip option** - email binding is mandatory
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.9, 1.10, 13.2, 13.4, 13.7_

- [ ] 12. Create username selection page component
  - URL: `/oauth2/username-selection?tempUserId={tempUserId}`
  - Display suggested username from GitHub as placeholder
  - Allow username modification
  - Show real-time validation feedback
  - Display format requirements (3-32 chars, alphanumeric + _-)
  - Show availability status (✓ available / ✗ taken)
  - Add "完成注册" button
  - Call `/oauth2/complete-registration` endpoint
  - Handle success → Login
  - Handle error → Show error message
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

- [ ] 13. Update OAuth2 login flow in frontend
  - Modify GitHub login button to call `/oauth2/github/authorize`
  - Handle callback redirect from GitHub
  - Check response for `requiresRegistration` flag
  - If true, redirect to email binding page with tempUserId
  - If false, proceed with normal login
  - _Requirements: 1.1, 3.1_

- [ ] 14. Implement account merge flow in frontend
  - Detect email conflict response from `/oauth2/complete-registration`
  - Show merge confirmation dialog
  - If user confirms, redirect to account recovery page
  - After recovery verification, call `/oauth2/complete-merge`
  - Handle merge completion and login
  - _Requirements: 5.6, 8.2, 8.3, 8.4_

### Phase 7: Configuration and Documentation

- [ ] 15. Update application configuration
  - Verify Redis configuration in application.yml
  - Verify OAuth2 GitHub configuration
  - Add configuration comments for state TTL
  - Add configuration comments for temp data TTL
  - _Requirements: 3.2_

- [ ] 16. Add logging and monitoring
  - Add INFO logs for successful OAuth2 operations
  - Add WARN logs for invalid state attempts (potential CSRF)
  - Add ERROR logs for OAuth provider failures
  - Add metrics for OAuth2 registration rate
  - Add metrics for state validation failure rate
  - _Requirements: 3.4, 18.1, 18.2, 18.3_

- [ ] 17. Write unit tests
  - Test state generation and validation
  - Test temp data storage and retrieval
  - Test email format validation
  - Test username validation
  - Test default password generation and encryption
  - Test account merge detection
  - Test email verification code generation and validation
  - Test GitHub email verification (no code)
  - Test custom email verification (with code)
  - Mock Redis operations
  - Mock email service
  - _Requirements: All_

- [ ] 18. Write integration tests
  - Test complete new user registration flow with GitHub email
  - Test complete new user registration flow with custom email
  - Test existing user login flow
  - Test account merge flow
  - Test state validation failure scenarios
  - Test temp data expiration handling
  - Test verification code expiration and retry
  - Mock GitHub OAuth2 provider
  - Mock email service
  - _Requirements: All_

- [ ] 19. Update API documentation
  - Document new `/oauth2/send-verification-code` endpoint
  - Document new `/oauth2/verify-email` endpoint
  - Document new `/oauth2/complete-registration` endpoint
  - Document new `/oauth2/initiate-merge` endpoint
  - Document new `/oauth2/complete-merge` endpoint
  - Document updated `/oauth2/github/callback` endpoint (with state)
  - Add request/response examples
  - Document error codes
  - Document email verification flow (GitHub vs custom)
  - _Requirements: All_

### Phase 8: Optional Enhancements

- [ ] 20. Add password change prompt for OAuth2 users (Optional)
  - Add indicator in user profile showing default password usage
  - Add "Change Password" prompt in account settings
  - Require email binding before password change
  - Send verification code to email before allowing password change
  - _Requirements: 2.3, 2.4, 2.5_

- [ ] 21. Add email change functionality (Optional)
  - Allow users to change their bound email
  - Send verification code to new email
  - Verify new email before updating
  - Update email in database after verification
  - _Requirements: 13.6_

- [ ] 22. Add multiple OAuth2 provider support (Optional)
  - Add Google OAuth2 provider configuration
  - Implement Google authorization URL generation
  - Implement Google callback handling
  - Allow users to link multiple providers
  - Show all linked providers in account settings
  - _Requirements: 9.1, 9.2, 9.5, 9.6_

## Task Execution Order

**Recommended execution order:**

1. **Phase 1 (Tasks 1-3):** Core OAuth2 enhancements - state validation and temp data storage
2. **Phase 2 (Tasks 4-5):** Registration completion logic
3. **Phase 3 (Tasks 6-7):** Account merging logic
4. **Phase 4 (Task 8):** Controller endpoints
5. **Phase 5 (Tasks 9-10):** Error handling
6. **Phase 6 (Tasks 11-14):** Frontend integration
7. **Phase 7 (Tasks 15-19):** Configuration, testing, and documentation
8. **Phase 8 (Tasks 20-22):** Optional enhancements (can be done later)

## Notes

- **No database changes required** - all functionality uses existing tables
- **Minimal new services** - all logic added to existing OAuth2ServiceImpl
- **Redis required** - ensure Redis is running for state and temp data storage
- **Backward compatible** - existing OAuth2 login flow still works
- **Transaction safety** - use @Transactional for multi-step operations
- **Security first** - state validation prevents CSRF attacks
- **User-friendly** - clear error messages and guided registration flow

## Testing Checklist

Before marking the feature complete, verify:

- [ ] State validation prevents CSRF attacks
- [ ] Expired state tokens are rejected
- [ ] New users must bind email (cannot skip)
- [ ] GitHub verified emails can be confirmed without code
- [ ] Custom emails require verification code
- [ ] Verification codes expire after 10 minutes
- [ ] Users can resend verification codes
- [ ] Invalid verification codes are rejected
- [ ] Username uniqueness is enforced
- [ ] Email format validation works
- [ ] Email conflicts are detected
- [ ] Account merge flow works correctly
- [ ] Default passwords are encrypted with BCrypt
- [ ] Existing user login still works
- [ ] Avatar updates work correctly
- [ ] Temp data expires after 30 minutes
- [ ] Verification codes are deleted after use
- [ ] All error scenarios are handled gracefully
- [ ] Logging captures important events
- [ ] Email service integration works
