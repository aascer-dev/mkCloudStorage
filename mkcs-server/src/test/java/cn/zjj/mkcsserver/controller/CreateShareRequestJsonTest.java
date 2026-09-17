package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.CreateShareRequest;
import com.zjj.mkcscommon.json.JacksonObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CreateShareRequestJsonTest {

    @Test
    void acceptsIsoLocalDateTimeFromTheShareForm() throws Exception {
        CreateShareRequest request = new JacksonObjectMapper().readValue("""
                {"fileId":7,"password":"aB3d","expiresAt":"2026-09-18T16:49:42"}
                """, CreateShareRequest.class);

        assertThat(request.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 16, 49, 42));
    }
}
