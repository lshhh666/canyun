package com.cloudmeal.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cloudmeal.constant.JwtClaimsConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.handler.GlobalExceptionHandler;
import com.cloudmeal.properties.JwtProperties;
import com.cloudmeal.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtContextCleanupTest {
    private static final String SECRET = "test-only-jwt-cleanup-secret-not-a-real-key";

    @AfterEach
    void cleanup() { BaseContext.removeCurrentId(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulAndFailedControllerRequestsBothReleaseIdentity(boolean admin) throws Exception {
        MockMvc mvc = mvc(interceptor(admin));
        String token = token(admin, SECRET, 60_000);
        BaseContext.setCurrentId(999L);
        mvc.perform(get("/identity").header("token", token))
                .andExpect(status().isOk()).andExpect(content().string("7"));
        assertNull(BaseContext.getCurrentId());
        mvc.perform(get("/failure").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertNull(BaseContext.getCurrentId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedAuthenticationClearsPreexistingIdentity(boolean admin) throws Exception {
        MockMvc mvc = mvc(interceptor(admin));
        for (String value : new String[]{null, "malformed-token", token(admin, SECRET, -60_000),
                token(admin, SECRET + "wrong", 60_000)}) {
            BaseContext.setCurrentId(999L);
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get("/identity");
            if (value != null) request.header("token", value);
            mvc.perform(request).andExpect(status().isUnauthorized());
            assertNull(BaseContext.getCurrentId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staticAndAsyncBranchesDoNotRetainThreadIdentity(boolean admin) throws Exception {
        AsyncHandlerInterceptor interceptor = interceptor(admin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BaseContext.setCurrentId(999L);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNull(BaseContext.getCurrentId());
        BaseContext.setCurrentId(7L);
        interceptor.afterConcurrentHandlingStarted(request, response, new Object());
        assertNull(BaseContext.getCurrentId());
        BaseContext.setCurrentId(7L);
        interceptor.afterCompletion(request, response, new Object(), new Exception("failure"));
        assertNull(BaseContext.getCurrentId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interceptorLogsNeverIncludeRawJwt(boolean admin) throws Exception {
        AsyncHandlerInterceptor interceptor = interceptor(admin);
        Logger logger = (Logger) LoggerFactory.getLogger(interceptor.getClass());
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String token = token(admin, SECRET, 60_000);
        try {
            mvc(interceptor).perform(get("/identity").header("token", token)).andExpect(status().isOk());
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(token)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private AsyncHandlerInterceptor interceptor(boolean admin) {
        JwtProperties properties = new JwtProperties();
        properties.setAdminSecretKey(SECRET);
        properties.setUserSecretKey(SECRET);
        properties.setAdminTokenName("token");
        properties.setUserTokenName("token");
        AsyncHandlerInterceptor interceptor = admin ? new JwtTokenAdminInterceptor() : new JwtTokenUserInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);
        return interceptor;
    }

    private String token(boolean admin, String key, long ttl) {
        return JwtUtil.createJWT(key, ttl, new HashMap<>(Collections.singletonMap(
                admin ? JwtClaimsConstant.EMP_ID : JwtClaimsConstant.USER_ID, 7L)));
    }

    private MockMvc mvc(AsyncHandlerInterceptor interceptor) {
        return MockMvcBuilders.standaloneSetup(new IdentityController()).addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @RestController
    static class IdentityController {
        @GetMapping("/identity")
        public Long identity() { return BaseContext.getCurrentId(); }
        @GetMapping("/failure")
        public void failure() { throw new BaseException("测试业务异常"); }
    }
}
