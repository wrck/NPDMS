package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Scriptable in-memory serial transport for tests; no real COM port is touched. */
final class InMemorySerialTransport implements SerialTransport {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataArrived = lock.newCondition();
    private final Deque<byte[]> responses = new ArrayDeque<>();
    private final List<byte[]> written = new ArrayList<>();
    private boolean open;
    private boolean failOpen;
    private CommandExecutionPort.SerialParams openedWith;

    void scriptResponse(String text) {
        lock.lock();
        try {
            responses.add(text.getBytes(StandardCharsets.UTF_8));
            dataArrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void failNextOpen() {
        this.failOpen = true;
    }

    List<String> writtenLines() {
        lock.lock();
        try {
            return written.stream().map(bytes -> new String(bytes, StandardCharsets.UTF_8)).toList();
        } finally {
            lock.unlock();
        }
    }

    CommandExecutionPort.SerialParams openedWith() {
        lock.lock();
        try {
            return openedWith;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void open(CommandExecutionPort.SerialParams params) throws IOException {
        if (failOpen) {
            throw new IOException("serial port is busy or cannot be opened: COMTEST");
        }
        lock.lock();
        try {
            open = true;
            openedWith = params;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(byte[] buffer, long timeoutMillis) throws IOException {
        lock.lock();
        try {
            if (!open) {
                throw new IOException("serial port is closed: COMTEST");
            }
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
            while (responses.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return 0;
                }
                try {
                    dataArrived.awaitNanos(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while reading serial port", exception);
                }
            }
            byte[] head = responses.peek();
            int count = Math.min(head.length, buffer.length);
            System.arraycopy(head, 0, buffer, 0, count);
            if (count == head.length) {
                responses.poll();
            } else {
                responses.poll();
                byte[] rest = new byte[head.length - count];
                System.arraycopy(head, count, rest, 0, rest.length);
                responses.push(rest);
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        lock.lock();
        try {
            if (!open) {
                throw new IOException("serial port is closed: COMTEST");
            }
            written.add(data.clone());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            open = false;
        } finally {
            lock.unlock();
        }
    }
}
