package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.io.IOException;

/** Isolates jSerialComm behind a narrow transport seam; test doubles implement the same contract. */
public interface SerialTransport extends AutoCloseable {

    /** Exclusively opens the port with the given parameters; fails if the port does not exist or is busy. */
    void open(CommandExecutionPort.SerialParams params) throws IOException;

    /** Reads up to buffer.length bytes, blocking at most timeoutMillis; returns 0 when no data arrived. */
    int read(byte[] buffer, long timeoutMillis) throws IOException;

    void write(byte[] data) throws IOException;

    @Override
    void close();
}
