package com.dp.deviceops.server;

import com.dp.deviceops.core.port.CommandExecutionPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

public final class MeteredCommandExecutionPort implements CommandExecutionPort {

    private final CommandExecutionPort delegate;
    private final MeterRegistry registry;

    public MeteredCommandExecutionPort(CommandExecutionPort delegate, MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        delegate.test(connection, secret, passphrase);
    }

    @Override
    public CommandResult execute(
            ConnectionSpec connection,
            char[] secret,
            char[] passphrase,
            String script,
            Duration timeout) {
        return record(connection, () -> delegate.execute(connection, secret, passphrase, script, timeout));
    }

    @Override
    public CommandResult execute(
            ConnectionSpec connection,
            char[] secret,
            char[] passphrase,
            String script,
            Duration timeout,
            ProgressListener listener) {
        return record(connection,
                () -> delegate.execute(connection, secret, passphrase, script, timeout, listener));
    }

    private CommandResult record(ConnectionSpec connection, Supplier<CommandResult> execution) {
        String protocol = connection.protocol().name();
        Timer.Sample sample = Timer.start(registry);
        String outcome = "failure";
        try {
            CommandResult result = execution.get();
            outcome = result.timedOut() ? "timeout" : "success";
            return result;
        } finally {
            Counter.builder("device_ops_command_executions_total")
                    .tag("protocol", protocol)
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();
            sample.stop(Timer.builder("device_ops_command_execution_duration")
                    .tag("protocol", protocol)
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }
}
