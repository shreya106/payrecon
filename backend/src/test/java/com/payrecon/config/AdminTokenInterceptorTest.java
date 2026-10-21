package com.payrecon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminTokenInterceptorTest {

    private final AdminTokenInterceptor interceptor = new AdminTokenInterceptor("secret-token");

    @Test
    void getRequestsArePublic() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recon/runs/latest");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void postWithoutTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/demo/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void postWithWrongTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/demo/generate");
        request.addHeader("X-Admin-Token", "guess");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void postWithCorrectTokenIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/demo/generate");
        request.addHeader("X-Admin-Token", "secret-token");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void emptyConfiguredTokenBlocksEverything() throws Exception {
        AdminTokenInterceptor misconfigured = new AdminTokenInterceptor("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/demo/generate");
        request.addHeader("X-Admin-Token", "");

        assertFalse(misconfigured.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}