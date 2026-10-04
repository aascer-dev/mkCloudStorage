package cn.zjj.mkcsserver.handler;

import cn.zjj.mkcsserver.auth.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerSecurityTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PermissionDeniedController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void permissionDeniedResponseDoesNotExposeRequiredPermissionName() throws Exception {
        mockMvc.perform(get("/test/permission-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(2004))
                .andExpect(jsonPath("$.message").value("权限不足"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal:write"))));
    }

    @Controller
    private static class PermissionDeniedController {

        @GetMapping("/test/permission-denied")
        @ResponseBody
        void rejectRequest() {
            throw new AccessDeniedException();
        }
    }
}
