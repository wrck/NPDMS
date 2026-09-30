package com.dp.deviceops.adapter.serial;

import com.fazecast.jSerialComm.SerialPort;

import java.util.Arrays;
import java.util.List;

/** Lists local serial port system names; contains no sensitive information. */
public final class JdkSerialPortEnumerator {

    public List<String> list() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
