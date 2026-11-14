package com.cinelode.backend.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ClientAddressResolver {
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isLoopback(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] addresses = forwardedFor.split(",");
        String clientAddress = addresses[addresses.length - 1].trim();
        return clientAddress.isEmpty() ? remoteAddress : clientAddress;
    }

    private boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
