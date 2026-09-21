package com.dp.deviceops.server;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureRemoteEndpointPolicyTest {

    @Test
    void pinsAllowedDnsNameToResolvedLiteralAddress() throws Exception {
        SecureRemoteEndpointPolicy policy = policyReturning("192.0.2.44");

        assertEquals("192.0.2.44", policy.resolveConnectAddress("device.example.test", 22));
    }

    @Test
    void allowsRfc1918Address() throws Exception {
        SecureRemoteEndpointPolicy policy = policyReturning("10.23.45.67");

        assertEquals("10.23.45.67", policy.resolveConnectAddress("private-device.example.test", 22));
    }

    @Test
    void rejectsAnyLocalLoopbackLinkLocalAndMulticastDestinations() throws Exception {
        for (String address : new String[]{"0.0.0.0", "127.0.0.1", "169.254.10.20", "224.0.0.1", "::1", "ff02::1"}) {
            SecureRemoteEndpointPolicy policy = policyReturning(address);
            assertThrows(IllegalArgumentException.class,
                    () -> policy.resolveConnectAddress("device.example.test", 22), address);
        }
    }

    @Test
    void rejectsAnswerSetContainingProhibitedDestination() throws Exception {
        SecureRemoteEndpointPolicy policy = new SecureRemoteEndpointPolicy(host -> new InetAddress[]{
                InetAddress.getByName("192.0.2.44"),
                InetAddress.getByName("127.0.0.1")
        });

        assertThrows(IllegalArgumentException.class,
                () -> policy.resolveConnectAddress("mixed-answer.example.test", 22));
    }

    @Test
    void rejectsLocalhostDomainWithoutResolvingIt() {
        AtomicBoolean resolverCalled = new AtomicBoolean();
        SecureRemoteEndpointPolicy policy = new SecureRemoteEndpointPolicy(host -> {
            resolverCalled.set(true);
            return new InetAddress[]{InetAddress.getByName("192.0.2.44")};
        });

        assertThrows(IllegalArgumentException.class, () -> policy.resolveConnectAddress("admin.LOCALHOST.", 22));
        assertFalse(resolverCalled.get());
    }

    @Test
    void reportsResolutionFailureWithoutReturningAnAddress() {
        SecureRemoteEndpointPolicy policy = new SecureRemoteEndpointPolicy(host -> {
            throw new UnknownHostException("resolver detail");
        });

        assertThrows(IllegalArgumentException.class,
                () -> policy.resolveConnectAddress("missing.example.test", 22));
    }

    private static SecureRemoteEndpointPolicy policyReturning(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new SecureRemoteEndpointPolicy(host -> new InetAddress[]{resolved});
    }
}
