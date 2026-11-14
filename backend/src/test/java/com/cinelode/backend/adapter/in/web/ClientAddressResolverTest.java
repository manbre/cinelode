package com.cinelode.backend.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver = new ClientAddressResolver();

    @Test
    void usesTheRightmostForwardedAddressOnlyForLoopbackProxyRequests() {
        MockHttpServletRequest proxiedRequest = new MockHttpServletRequest();
        proxiedRequest.setRemoteAddr("127.0.0.1");
        proxiedRequest.addHeader("X-Forwarded-For", "192.0.2.10, 192.168.1.25");

        assertEquals("192.168.1.25", resolver.resolve(proxiedRequest));
    }

    @Test
    void ignoresForwardedHeadersForDirectNonLoopbackClients() {
        MockHttpServletRequest directRequest = new MockHttpServletRequest();
        directRequest.setRemoteAddr("192.168.1.10");
        directRequest.addHeader("X-Forwarded-For", "192.0.2.10");

        assertEquals("192.168.1.10", resolver.resolve(directRequest));
    }
}
