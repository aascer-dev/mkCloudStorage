#!/bin/bash

# JWT Token 验证脚本

echo "=========================================="
echo "JWT Token 验证脚本"
echo "=========================================="
echo ""

# 1. 登录获取 Token
echo "1️⃣  正在登录..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456",
    "rememberMe": false
  }')

echo "响应："
echo "$RESPONSE" | jq '.'
echo ""

# 2. 提取 Token
TOKEN=$(echo "$RESPONSE" | jq -r '.data.token')
echo "2️⃣  提取的 Token："
echo "$TOKEN"
echo ""

# 3. 检查 Token 格式
echo "3️⃣  检查 Token 格式..."
if [[ $TOKEN == *"."* ]]; then
  PART_COUNT=$(echo "$TOKEN" | tr -cd '.' | wc -c)
  PART_COUNT=$((PART_COUNT + 1))
  
  if [ $PART_COUNT -eq 3 ]; then
    echo "✅ Token 是 JWT 格式（3 部分）"
  else
    echo "❌ Token 格式错误（$PART_COUNT 部分）"
  fi
else
  echo "❌ Token 不是 JWT 格式（没有 . 分隔符）"
  echo "   这是 UUID Token，说明配置还没生效"
fi
echo ""

# 4. 解析 Token
echo "4️⃣  解析 Token..."
echo "访问 https://jwt.io 粘贴以下 Token："
echo "$TOKEN"
echo ""

# 5. 使用 Token 请求
echo "5️⃣  使用 Token 请求用户信息..."
curl -s -X GET http://localhost:8080/api/users/info \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

echo "=========================================="
echo "验证完成"
echo "=========================================="
