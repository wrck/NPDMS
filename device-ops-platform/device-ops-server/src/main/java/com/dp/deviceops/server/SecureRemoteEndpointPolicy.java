package com.dp.deviceops.server;

import com.dp.deviceops.core.port.RemoteEndpointPolicy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

public final class SecureRemoteEndpointPolicy implements RemoteEndpointPolicy {

    private static final int MAX_HOST_LENGTH = 253;

    private final HostResolver resolver;

    public SecureRemoteEndpointPolicy() {
        this(InetAddress::getAllByName);
    }

    SecureRemoteEndpointPolicy(HostResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public String resolveConnectAddress(String host, int port) {
        String normalizedHost = normalizeHost(host);
        validatePort(port);
        InetAddress[] addresses = resolve(normalizedHost);
        if (addresses.length == 0) {
            throw new IllegalArgumentException("remote endpoint did not resolve");
        }
        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                throw new IllegalArgumentException("remote endpoint is not allowed");
            }
        }
        return addresses[0].getHostAddress();
    }

    private InetAddress[] resolve(String host) {
        try {
            return resolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("remote endpoint could not be resolved", exception);
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("remote endpoint host is required");
        }
        String normalized = host.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("remote endpoint host is invalid");
        }
        String domain = normalized.endsWith(".")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        String lowerCaseDomain = domain.toLowerCase(Locale.ROOT);
        if (lowerCaseDomain.equals("localhost") || lowerCaseDomain.endsWith(".localhost")) {
            throw new IllegalArgumentException("remote endpoint host is not allowed");
        }
        return normalized;
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("remote endpoint port is invalid");
        }
    }

    private static boolean isForbidden(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress();
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
