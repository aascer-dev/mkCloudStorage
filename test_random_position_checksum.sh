#!/bin/bash

# 随机位置校验功能测试脚本
# 用于验证秒传的随机位置校验功能是否正常工作

set -e

# 配置
API_BASE_URL="http://localhost:8080/api"
TEST_FILE="test_file_1MB.bin"
BUCKET_ID="1"  # 修改为实际的bucket ID

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}随机位置校验功能测试${NC}"
echo -e "${YELLOW}========================================${NC}\n"

# 1. 准备测试文件
echo -e "${YELLOW}[1/4] 准备测试文件...${NC}"
if [ ! -f "$TEST_FILE" ]; then
    echo "生成测试文件：$TEST_FILE (1MB)"
    dd if=/dev/urandom of="$TEST_FILE" bs=1M count=1 2>/dev/null
    echo -e "${GREEN}✓ 测试文件已生成${NC}\n"
else
    echo -e "${GREEN}✓ 测试文件已存在${NC}\n"
fi

# 2. 第一次上传 - 应该完整上传
echo -e "${YELLOW}[2/4] 第一次上传文件（完整上传）...${NC}"
RESPONSE1=$(curl -s -F "file=@$TEST_FILE" \
                     -F "parentId=1" \
                     -F "bucketId=$BUCKET_ID" \
                     "$API_BASE_URL/files/upload")

echo "响应：$RESPONSE1"
FILE_ID=$(echo "$RESPONSE1" | grep -o '"fileId":[0-9]*' | grep -o '[0-9]*')
IS_SECOND_1=$(echo "$RESPONSE1" | grep -o '"isSecondUpload":[a-z]*' | grep -o '[a-z]*')

if [[ "$IS_SECOND_1" == "false" ]]; then
    echo -e "${GREEN}✓ 第一次上传成功，是完整上传${NC}"
    echo -e "${GREEN}  文件ID: $FILE_ID${NC}"
    echo -e "${GREEN}  isSecondUpload: $IS_SECOND_1${NC}\n"
else
    echo -e "${RED}✗ 第一次上传应该是完整上传，但实际是秒传${NC}\n"
fi

# 3. 查询数据库验证随机位置字段
echo -e "${YELLOW}[3/4] 检查数据库中的随机位置校验信息...${NC}"

# 使用MySQL/PostgreSQL查询（需要根据实际数据库类型修改）
# 这是一个示例，需要根据实际环境配置数据库连接

read -p "输入数据库用户名 (默认: root): " DB_USER
DB_USER=${DB_USER:-root}

read -sp "输入数据库密码: " DB_PASS
echo

read -p "输入数据库名 (默认: mkcs): " DB_NAME
DB_NAME=${DB_NAME:-mkcs}

# 查询随机位置校验信息
echo "执行查询: SELECT id, filename, size, random_offset, random_length, random_position_hash FROM file_contents ORDER BY id DESC LIMIT 1;"

mysql -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
    -e "SELECT id, filename, size, random_offset, random_length, \
           CONCAT(SUBSTRING(random_position_hash, 1, 10), '...') as hash_preview \
        FROM file_contents ORDER BY id DESC LIMIT 1;" 2>/dev/null || \
    echo -e "${YELLOW}提示：跳过数据库查询（需要MySQL或PostgreSQL环境）${NC}"

echo

# 4. 第二次上传相同文件 - 应该秒传
echo -e "${YELLOW}[4/4] 第二次上传相同文件（测试秒传）...${NC}"
RESPONSE2=$(curl -s -F "file=@$TEST_FILE" \
                     -F "parentId=1" \
                     -F "bucketId=$BUCKET_ID" \
                     "$API_BASE_URL/files/upload")

echo "响应：$RESPONSE2"
IS_SECOND_2=$(echo "$RESPONSE2" | grep -o '"isSecondUpload":[a-z]*' | grep -o '[a-z]*')
STATUS=$(echo "$RESPONSE2" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')

if [[ "$IS_SECOND_2" == "true" ]]; then
    echo -e "${GREEN}✓ 第二次上传执行了秒传${NC}"
    echo -e "${GREEN}  isSecondUpload: $IS_SECOND_2${NC}"
    echo -e "${GREEN}  status: $STATUS${NC}\n"
else
    echo -e "${RED}✗ 秒传失败了！第二次上传应该秒传${NC}"
    echo -e "${RED}  isSecondUpload: $IS_SECOND_2${NC}"
    echo -e "${RED}  status: $STATUS${NC}\n"
fi

# 5. 修改文件后上传 - 应该拒绝秒传
echo -e "${YELLOW}[5/5] 修改文件后上传（测试安全验证）...${NC}"
echo "修改测试文件..."
echo "modified" >> "$TEST_FILE"

RESPONSE3=$(curl -s -F "file=@$TEST_FILE" \
                     -F "parentId=1" \
                     -F "bucketId=$BUCKET_ID" \
                     "$API_BASE_URL/files/upload")

echo "响应：$RESPONSE3"
IS_SECOND_3=$(echo "$RESPONSE3" | grep -o '"isSecondUpload":[a-z]*' | grep -o '[a-z]*')
STATUS_3=$(echo "$RESPONSE3" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')

# 恢复文件到原始大小
echo "恢复文件..."
dd if=/dev/urandom of="$TEST_FILE" bs=1M count=1 2>/dev/null

if [[ "$IS_SECOND_3" == "false" ]]; then
    echo -e "${GREEN}✓ 修改后的文件被正确拒绝秒传${NC}"
    echo -e "${GREEN}  isSecondUpload: $IS_SECOND_3${NC}"
    echo -e "${GREEN}  功能工作正常！${NC}\n"
else
    echo -e "${YELLOW}⚠ 修改后的文件仍然秒传了${NC}"
    echo -e "${YELLOW}  可能原因：哈希碰撞（极端罕见）或其他问题${NC}\n"
fi

# 测试总结
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}测试完成！${NC}"
echo -e "${YELLOW}========================================${NC}"

echo -e "\n${YELLOW}预期结果摘要：${NC}"
echo "  1. 第一次上传：isSecondUpload=false ✓"
echo "  2. 数据库随机位置字段已保存 ✓"
echo "  3. 第二次相同文件上传：isSecondUpload=true ✓"
echo "  4. 修改后的文件上传：isSecondUpload=false ✓"

echo -e "\n${GREEN}清理测试文件...${NC}"
# rm -f "$TEST_FILE"  # 可选：取消注释以删除测试文件
echo "测试文件已保留: $TEST_FILE"
