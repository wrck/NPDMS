package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ProtocolCommandExecutionRouter implements CommandExecutionPort {

    private final Map<ConnectionProtocol, ProtocolCommandExecutionAdapter> adapters;

    public ProtocolCommandExecutionRouter(Collection<? extends ProtocolCommandExecutionAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters must not be null");
        EnumMap<ConnectionProtocol, ProtocolCommandExecutionAdapter> indexed =
                new EnumMap<>(ConnectionProtocol.class);
        for (ProtocolCommandExecutionAdapter adapter : adapters) {
            ProtocolCommandExecutionAdapter required = Objects.requireNonNull(adapter, "adapter must not be null");
            ConnectionProtocol protocol = Objects.requireNonNull(required.protocol(), "adapter protocol must not be null");
            if (indexed.putIfAbsent(protocol, required) != null) {
                throw new IllegalArgumentException("duplicate adapter for protocol " + protocol);
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        adapterFor(connection).test(connection, secret, passphrase);
    }

    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                 Duration timeout) {
        return adapterFor(connection).execute(connection, secret, passphrase, script, timeout);
    }

    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                 Duration timeout, ProgressListener listener) {
        Objects.requireNonNull(listener, "listener");
        return adapterFor(connection).execute(connection, secret, passphrase, script, timeout, listener);
    }

    private ProtocolCommandExecutionAdapter adapterFor(ConnectionSpec connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        ProtocolCommandExecutionAdapter adapter = adapters.get(connection.protocol());
        if (adapter == null) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED,
                    ConnectionFailure.Stage.CONNECT, "connection protocol is disabled");
        }
        return adapter;
    }
}
