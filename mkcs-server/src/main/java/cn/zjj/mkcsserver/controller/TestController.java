package cn.zjj.mkcsserver.controller;

import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test Controller for encoding verification
 */
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Test APIs for encoding verification")
public class TestController {

    /**
     * Test Chinese encoding
     */
    @GetMapping("/encoding")
    @Operation(summary = "Test Encoding", description = "Test Chinese character encoding")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test successful")
    })
    public Result<Map<String, Object>> testEncoding() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "测试中文编码");
        data.put("description", "这是一个测试中文字符编码的接口");
        data.put("status", "正常");
        data.put("timestamp", System.currentTimeMillis());
        
        return Result.success("测试成功", data);
    }

    /**
     * Test English encoding
     */
    @GetMapping("/english")
    @Operation(summary = "Test English", description = "Test English character encoding")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test successful")
    })
    public Result<Map<String, Object>> testEnglish() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Test English encoding");
        data.put("description", "This is a test interface for English character encoding");
        data.put("status", "OK");
        data.put("timestamp", System.currentTimeMillis());
        
        return Result.success("Test successful", data);
    }
}