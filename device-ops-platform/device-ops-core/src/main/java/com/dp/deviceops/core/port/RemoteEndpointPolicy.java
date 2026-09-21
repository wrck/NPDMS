package com.dp.deviceops.core.port;

@FunctionalInterface
public interface RemoteEndpointPolicy {

    String resolveConnectAddress(String host, int port);
}
