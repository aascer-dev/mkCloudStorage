package cn.zjj.mkcsserver.auth;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.service.UsersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UsersService usersService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider, usersService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void propagatesDownstreamExceptionInsteadOfConvertingItToUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test-user", 1L);
        Users user = new Users();
        user.setId(1L);
        user.setStatus((byte) 1);
        user.setTokenVersion(1L);
        FilterChain failingChain = (servletRequest, servletResponse) -> {
            assertThat(UserContext.get()).isEqualTo(authenticatedUser);
            throw new IllegalStateException("downstream failure");
        };

        when(jwtProvider.parseAccessToken("access-token")).thenReturn(authenticatedUser);
        when(usersService.getById(1L)).thenReturn(user);

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failure");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void propagatesUserLookupFailureInsteadOfConvertingItToUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test-user", 1L);

        when(jwtProvider.parseAccessToken("access-token")).thenReturn(authenticatedUser);
        when(usersService.getById(1L)).thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(UserContext.get()).isNull();
    }
}
