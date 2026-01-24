package cn.zjj.mkcsserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MkcsServerApplicationTests {

    @Test
    void contextLoads() {
        // 简单的上下文加载测试
    }

}
