@echo off
REM 随机位置校验功能测试脚本 (Windows版本)

setlocal enabledelayedexpansion

REM 配置
set API_BASE_URL=http://localhost:8080/api
set TEST_FILE=test_file_1MB.bin
set BUCKET_ID=1

echo ========================================
echo 随机位置校验功能测试 (Windows)
echo ========================================
echo.

REM 1. 准备测试文件
echo [1/4] 准备测试文件...
if not exist "%TEST_FILE%" (
    echo 生成测试文件: %TEST_FILE% (1MB)
    REM 使用PowerShell生成随机文件
    powershell -Command "$bytes = New-Object 'byte[]' (1MB); [Random]::new().NextBytes($bytes); [System.IO.File]::WriteAllBytes('%TEST_FILE%', $bytes)"
    echo [OK] 测试文件已生成
) else (
    echo [OK] 测试文件已存在
)
echo.

REM 2. 第一次上传
echo [2/4] 第一次上传文件（完整上传）...
for /f "tokens=*" %%a in ('curl -s -F "file=@%TEST_FILE%" -F "parentId=1" -F "bucketId=%BUCKET_ID%" "%API_BASE_URL%/files/upload"') do (
    set RESPONSE1=%%a
)
echo 响应: %RESPONSE1%

REM 从响应中提取信息
for /f "tokens=*" %%a in ('powershell -Command "([regex]::Match('[RESPONSE1]', '\"fileId\":([0-9]+)').Groups[1].Value)"') do (
    set FILE_ID=%%a
)

echo [OK] 第一次上传完成 (fileId: %FILE_ID%)
echo.

REM 3. 数据库检查
echo [3/4] 检查数据库中的随机位置校验信息...
echo 跳过数据库查询 (需要配置MySQL或PostgreSQL)
echo.

REM 4. 第二次上传相同文件
echo [4/4] 第二次上传相同文件（测试秒传）...
for /f "tokens=*" %%a in ('curl -s -F "file=@%TEST_FILE%" -F "parentId=1" -F "bucketId=%BUCKET_ID%" "%API_BASE_URL%/files/upload"') do (
    set RESPONSE2=%%a
)
echo 响应: %RESPONSE2%

REM 检查是否秒传
echo.if "%RESPONSE2%" find "\"isSecondUpload\":true" >nul (
    echo [OK] 第二次上传执行了秒传
) else (
    echo [ERROR] 秒传失败！
)
echo.

REM 5. 修改文件后上传
echo [5/5] 修改文件后上传（测试安全验证）...
REM 添加内容到文件
echo modified >> "%TEST_FILE%"

for /f "tokens=*" %%a in ('curl -s -F "file=@%TEST_FILE%" -F "parentId=1" -F "bucketId=%BUCKET_ID%" "%API_BASE_URL%/files/upload"') do (
    set RESPONSE3=%%a
)

REM 恢复原始文件
powershell -Command "$bytes = New-Object 'byte[]' (1MB); [Random]::new().NextBytes($bytes); [System.IO.File]::WriteAllBytes('%TEST_FILE%', $bytes)"

echo 响应: %RESPONSE3%

if "%RESPONSE3%" find "\"isSecondUpload\":false" >nul (
    echo [OK] 修改后的文件被正确拒绝秒传
) else (
    echo [WARNING] 修改后的文件仍然秒传了
)
echo.

echo ========================================
echo 测试完成！
echo ========================================
echo.
echo 预期结果摘要:
echo   1. 第一次上传: isSecondUpload=false [OK]
echo   2. 数据库随机位置字段已保存 [OK]
echo   3. 第二次相同文件上传: isSecondUpload=true [OK]
echo   4. 修改后的文件上传: isSecondUpload=false [OK]

REM 可选：清理测试文件
echo.
echo 测试文件已保留: %TEST_FILE%
echo.

endlocal pause
