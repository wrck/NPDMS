package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/** Production SerialTransport over a local COM port via jSerialComm. */
public final class JdkSerialTransport implements SerialTransport {

    private final String comPort;
    private SerialPort port;

    public JdkSerialTransport(String comPort) {
        if (comPort == null || comPort.isBlank()) {
            throw new IllegalArgumentException("comPort is required");
        }
        this.comPort = comPort.strip();
    }

    @Override
    public void open(CommandExecutionPort.SerialParams params) throws IOException {
        SerialPort candidate = Arrays.stream(SerialPort.getCommPorts())
                .filter(existing -> existing.getSystemPortName().equalsIgnoreCase(comPort))
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            throw new IOException("serial port does not exist: " + comPort);
        }
        candidate.setBaudRate(params.baudRate());
        candidate.setNumDataBits(params.dataBits());
        candidate.setNumStopBits(params.stopBits());
        candidate.setParity(switch (params.parity()) {
            case NONE -> SerialPort.NO_PARITY;
            case EVEN -> SerialPort.EVEN_PARITY;
            case ODD -> SerialPort.ODD_PARITY;
            case MARK -> SerialPort.MARK_PARITY;
            case SPACE -> SerialPort.SPACE_PARITY;
        });
        candidate.setFlowControl(switch (params.flowControl()) {
            case NONE -> SerialPort.FLOW_CONTROL_DISABLED;
            case RTS_CTS -> SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case XON_XOFF -> SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
        });
        candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        if (!candidate.openPort()) {
            throw new IOException("serial port is busy or cannot be opened: " + comPort);
        }
        port = candidate;
    }

    @Override
    public int read(byte[] buffer, long timeoutMillis) throws IOException {
        SerialPort current = port;
        if (current == null || !current.isOpen()) {
            throw new IOException("serial port is closed: " + comPort);
        }
        // jSerialComm 2.11.0 takes bytes-to-read (int) and reads the timeout from the port configuration.
        current.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,
                (int) Math.min(timeoutMillis, Integer.MAX_VALUE), 0);
        int read = current.readBytes(buffer, buffer.length);
        return read > 0 ? read : 0;
    }

    @Override
    public void write(byte[] data) throws IOException {
        SerialPort current = port;
        if (current == null || !current.isOpen()) {
            throw new IOException("serial port is closed: " + comPort);
        }
        if (current.writeBytes(data, data.length) != data.length) {
            throw new IOException("serial port write failed: " + comPort);
        }
    }

    @Override
    public void close() {
        if (port != null) {
            port.closePort();
            port = null;
        }
    }

    String comPort() {
        return comPort.toLowerCase(Locale.ROOT);
    }
}
