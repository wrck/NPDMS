package com.dp.deviceops.server;

import com.dp.deviceops.adapter.ssh.mina.MinaCommandExecutionAdapter;
import com.dp.deviceops.adapter.telnet.TelnetCommandExecutionAdapter;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CommandExecutionPort.AuthenticationType;
import com.dp.deviceops.core.port.CommandExecutionPort.CommandResult;
import com.dp.deviceops.core.port.CommandExecutionPort.ConnectionSpec;
import com.dp.deviceops.core.port.CommandExecutionPort.ExecutionMode;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputProgress;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;
import com.dp.deviceops.core.port.CommandExecutionPort.TelnetLineEnding;
import com.dp.deviceops.core.port.CommandExecutionPort.TelnetPrompts;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedProtocolPaginationConcurrencyTest {

    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password";
    private static final String COMMAND_PROMPT = "generic#";
    private static final String PAGER_PROMPT = "--More--";
    private static final int PAGE_COUNT = 3;
    private static final int TASK_COUNT = 4;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(8);

    @Test
    @Timeout(30)
    void keepsMixedSshAndTelnetPaginationOutputPageCountsAndControlInputTaskLocal() throws Exception {
        CountDownLatch commandArrivals = new CountDownLatch(TASK_COUNT);
        try (EmbeddedSshEndpoint sshEndpoint = new EmbeddedSshEndpoint(commandArrivals);
             EmbeddedTelnetEndpoint telnetEndpoint = new EmbeddedTelnetEndpoint(commandArrivals);
             MinaCommandExecutionAdapter sshAdapter = new MinaCommandExecutionAdapter(
                     (host, port) -> host, 1024 * 1024, 256,
                     Pattern.compile(Pattern.quote(COMMAND_PROMPT) + "$"), 32)) {
            TelnetCommandExecutionAdapter telnetAdapter = new TelnetCommandExecutionAdapter(
                    (host, port) -> host, true, 1024 * 1024, 256, 32);
            ConnectionSpec sshConnection = sshConnection(sshEndpoint.port());
            ConnectionSpec telnetConnection = telnetConnection(telnetEndpoint.port());
            List<TaskCase> tasks = List.of(
                    new TaskCase(ConnectionProtocol.SSH2, "ssh-alpha", sshAdapter, sshConnection),
                    new TaskCase(ConnectionProtocol.SSH2, "ssh-bravo", sshAdapter, sshConnection),
                    new TaskCase(ConnectionProtocol.TELNET, "telnet-alpha", telnetAdapter, telnetConnection),
                    new TaskCase(ConnectionProtocol.TELNET, "telnet-bravo", telnetAdapter, telnetConnection));

            ExecutorService executor = Executors.newFixedThreadPool(TASK_COUNT);
            try {
                List<Future<ExecutionEvidence>> futures = tasks.stream()
                        .map(task -> executor.submit(() -> execute(task)))
                        .toList();
                List<ExecutionEvidence> evidence = new ArrayList<>(TASK_COUNT);
                for (Future<ExecutionEvidence> future : futures) {
                    evidence.add(await(future));
                }

                assertEquals(0, commandArrivals.getCount(), "all protocol tasks must overlap at the endpoint gate");
                assertEquals(TASK_COUNT, evidence.stream()
                        .map(item -> item.result().stdout())
                        .collect(Collectors.toSet()).size());
                for (ExecutionEvidence item : evidence) {
                    assertTaskEvidence(item, tasks);
                    List<Integer> receivedContinuations = item.task().protocol() == ConnectionProtocol.SSH2
                            ? sshEndpoint.continuations(item.task().command())
                            : telnetEndpoint.continuations(item.task().command());
                    assertEquals(List.of(32, 32, 32), receivedContinuations,
                            "the endpoint must receive exactly one space continuation per page");
                }
                sshEndpoint.assertHealthy();
                telnetEndpoint.assertHealthy();
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                        "mixed-protocol executor did not terminate");
            }
        }
    }

    private static ExecutionEvidence execute(TaskCase task) {
        List<OutputProgress> progress = new ArrayList<>();
        CommandResult result = task.adapter().execute(
                task.connection(), PASSWORD.toCharArray(), new char[0],
                task.command(), COMMAND_TIMEOUT, progress::add);
        return new ExecutionEvidence(task, result, List.copyOf(progress));
    }

    private static ExecutionEvidence await(Future<ExecutionEvidence> future) throws Exception {
        try {
            return future.get(12, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new AssertionError("mixed-protocol task failed", cause);
        }
    }

    private static void assertTaskEvidence(ExecutionEvidence evidence, List<TaskCase> allTasks) {
        CommandResult result = evidence.result();
        String expectedOutput = expectedOutput(evidence.task().command());
        String recordedProgress = evidence.progress().stream()
                .filter(progress -> progress.streamType() == OutputStreamType.STDOUT)
                .map(OutputProgress::content)
                .collect(Collectors.joining());

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertFalse(result.truncated());
        assertEquals("", result.stderr());
        assertEquals(expectedOutput, result.stdout());
        assertEquals(expectedOutput, recordedProgress,
                "persistable progress must match the task's returned output");
        assertEquals(PAGE_COUNT, evidence.progress().stream()
                .mapToInt(OutputProgress::pageCount)
                .max()
                .orElseThrow());
        assertFalse(recordedProgress.contains(PAGER_PROMPT));
        assertFalse(recordedProgress.contains(" "),
                "pager continuation spaces must not enter output progress records");
        for (TaskCase other : allTasks) {
            if (other != evidence.task()) {
                assertFalse(result.stdout().contains(other.command()),
                        () -> evidence.task().command() + " contains output from " + other.command());
            }
        }
    }

    private static String expectedOutput(String command) {
        StringBuilder output = new StringBuilder();
        for (int page = 1; page <= PAGE_COUNT; page++) {
            output.append(command).append(":p").append(page).append('\n');
        }
        return output.append(COMMAND_PROMPT).toString();
    }

    private static ConnectionSpec sshConnection(int port) {
        return new ConnectionSpec(
                "127.0.0.1", port, USERNAME, AuthenticationType.PASSWORD,
                ExecutionMode.SHELL, null, Duration.ofSeconds(3));
    }

    private static ConnectionSpec telnetConnection(int port) {
        return new ConnectionSpec(
                ConnectionProtocol.TELNET, "127.0.0.1", port, USERNAME,
                AuthenticationType.PASSWORD, ExecutionMode.SHELL, null,
                new TelnetPrompts("login:$", "password:$", "generic#$", TelnetLineEnding.LF),
                Duration.ofSeconds(3));
    }

    private record TaskCase(
            ConnectionProtocol protocol,
            String command,
            CommandExecutionPort adapter,
            ConnectionSpec connection) {
    }

    private record ExecutionEvidence(
            TaskCase task,
            CommandResult result,
            List<OutputProgress> progress) {
    }

    private static final class EmbeddedSshEndpoint implements AutoCloseable {

        private final CountDownLatch commandArrivals;
        private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        private final Map<String, CopyOnWriteArrayList<Integer>> continuations = new ConcurrentHashMap<>();
        private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
        private final SshServer server;

        private EmbeddedSshEndpoint(CountDownLatch commandArrivals) throws Exception {
            this.commandArrivals = commandArrivals;
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair hostKey = generator.generateKeyPair();
            server = SshServer.setUpDefaultServer();
            server.setHost("127.0.0.1");
            server.setPort(0);
            server.setKeyPairProvider(session -> List.of(hostKey));
            server.setPasswordAuthenticator((username, password, session) ->
                    USERNAME.equals(username) && PASSWORD.equals(password));
            server.setShellFactory(channel -> new PagedShellCommand(this));
            server.start();
        }

        private int port() {
            return server.getPort();
        }

        private List<Integer> continuations(String command) {
            return List.copyOf(continuations.getOrDefault(command, new CopyOnWriteArrayList<>()));
        }

        private void recordContinuation(String command, int value) {
            continuations.computeIfAbsent(command, ignored -> new CopyOnWriteArrayList<>()).add(value);
        }

        private void awaitOtherCommands() throws InterruptedException, IOException {
            commandArrivals.countDown();
            if (!commandArrivals.await(5, TimeUnit.SECONDS)) {
                throw new IOException("mixed-protocol command gate timed out");
            }
        }

        private void assertHealthy() {
            assertTrue(failures.isEmpty(), () -> "embedded SSH endpoint failed: " + failures);
        }

        @Override
        public void close() throws IOException, InterruptedException {
            server.stop(true);
            for (Thread worker : List.copyOf(workers)) {
                worker.interrupt();
                worker.join(Duration.ofSeconds(2));
            }
        }
    }

    private static final class PagedShellCommand implements Command {

        private final EmbeddedSshEndpoint endpoint;
        private final AtomicReference<Thread> worker = new AtomicReference<>();
        private InputStream input;
        private OutputStream output;
        private ExitCallback exitCallback;

        private PagedShellCommand(EmbeddedSshEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void setInputStream(InputStream input) {
            this.input = input;
        }

        @Override
        public void setOutputStream(OutputStream output) {
            this.output = output;
        }

        @Override
        public void setErrorStream(OutputStream error) {
            // This endpoint emits only stdout.
        }

        @Override
        public void setExitCallback(ExitCallback exitCallback) {
            this.exitCallback = exitCallback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            Thread commandWorker = Thread.ofVirtual().name("mixed-pagination-ssh").unstarted(this::run);
            worker.set(commandWorker);
            endpoint.workers.add(commandWorker);
            commandWorker.start();
        }

        private void run() {
            try {
                writeAscii(output, COMMAND_PROMPT);
                String command = readLine(input);
                endpoint.awaitOtherCommands();
                writePages(command, input, output, endpoint::recordContinuation);
                exitCallback.onExit(0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                endpoint.failures.add(exception);
            } catch (Throwable failure) {
                endpoint.failures.add(failure);
                exitCallback.onExit(1, "embedded SSH endpoint failed");
            } finally {
                endpoint.workers.remove(Thread.currentThread());
            }
        }

        @Override
        public void destroy(ChannelSession channel) throws InterruptedException {
            Thread commandWorker = worker.getAndSet(null);
            if (commandWorker != null) {
                commandWorker.interrupt();
                commandWorker.join(Duration.ofSeconds(2));
            }
        }
    }

    private static final class EmbeddedTelnetEndpoint implements AutoCloseable {

        private final CountDownLatch commandArrivals;
        private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        private final Map<String, CopyOnWriteArrayList<Integer>> continuations = new ConcurrentHashMap<>();
        private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
        private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ServerSocket serverSocket;
        private final Thread acceptWorker;

        private EmbeddedTelnetEndpoint(CountDownLatch commandArrivals) throws IOException {
            this.commandArrivals = commandArrivals;
            serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            acceptWorker = Thread.ofVirtual().name("mixed-pagination-telnet-accept").unstarted(this::accept);
            acceptWorker.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private List<Integer> continuations(String command) {
            return List.copyOf(continuations.getOrDefault(command, new CopyOnWriteArrayList<>()));
        }

        private void accept() {
            while (!closed.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    clients.add(socket);
                    Thread worker = Thread.ofVirtual().name("mixed-pagination-telnet").unstarted(
                            () -> handle(socket));
                    workers.add(worker);
                    worker.start();
                } catch (SocketException exception) {
                    if (!closed.get()) {
                        failures.add(exception);
                    }
                    return;
                } catch (IOException exception) {
                    failures.add(exception);
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                writeAscii(output, "login:");
                assertEquals(USERNAME, readTelnetLine(input));
                writeAscii(output, "password:");
                assertEquals(PASSWORD, readTelnetLine(input));
                writeAscii(output, COMMAND_PROMPT);
                String command = readTelnetLine(input);
                commandArrivals.countDown();
                if (!commandArrivals.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("mixed-protocol command gate timed out");
                }
                writePages(command, input, output,
                        (receivedCommand, value) -> continuations
                                .computeIfAbsent(receivedCommand, ignored -> new CopyOnWriteArrayList<>())
                                .add(value));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures.add(exception);
            } catch (Throwable failure) {
                failures.add(failure);
            } finally {
                clients.remove(socket);
                workers.remove(Thread.currentThread());
            }
        }

        private void assertHealthy() {
            assertTrue(failures.isEmpty(), () -> "embedded Telnet endpoint failed: " + failures);
        }

        @Override
        public void close() throws IOException, InterruptedException {
            closed.set(true);
            serverSocket.close();
            for (Socket client : List.copyOf(clients)) {
                client.close();
            }
            acceptWorker.join(Duration.ofSeconds(2));
            for (Thread worker : List.copyOf(workers)) {
                worker.interrupt();
                worker.join(Duration.ofSeconds(2));
            }
        }
    }

    private static void writePages(
            String command,
            InputStream input,
            OutputStream output,
            ContinuationRecorder recorder) throws IOException {
        for (int page = 1; page <= PAGE_COUNT; page++) {
            writeAscii(output, command + ":p" + page + "\n" + PAGER_PROMPT);
            int continuation = input.read();
            if (continuation < 0) {
                throw new EOFException("client closed before pager continuation");
            }
            recorder.record(command, continuation);
            if (continuation != ' ') {
                throw new IOException("unexpected pager continuation byte: " + continuation);
            }
        }
        writeAscii(output, COMMAND_PROMPT);
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\r') {
                continue;
            }
            if (value == '\n') {
                return line.toString();
            }
            line.append((char) value);
        }
        throw new EOFException("client closed before line ending");
    }

    private static String readTelnetLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = readTelnetApplicationByte(input)) >= 0) {
            if (value == '\r') {
                continue;
            }
            if (value == '\n') {
                return line.toString();
            }
            line.append((char) value);
        }
        throw new EOFException("Telnet client closed before line ending");
    }

    private static int readTelnetApplicationByte(InputStream input) throws IOException {
        while (true) {
            int value = input.read();
            if (value != 255) {
                return value;
            }
            int command = input.read();
            if (command < 0) {
                return -1;
            }
            if (command == 255) {
                return 255;
            }
            if (command >= 251 && command <= 254) {
                if (input.read() < 0) {
                    return -1;
                }
            } else if (command == 250) {
                skipTelnetSubnegotiation(input);
            }
        }
    }

    private static void skipTelnetSubnegotiation(InputStream input) throws IOException {
        int previous = -1;
        int value;
        while ((value = input.read()) >= 0) {
            if (previous == 255 && value == 240) {
                return;
            }
            previous = value;
        }
    }

    @FunctionalInterface
    private interface ContinuationRecorder {
        void record(String command, int value);
    }
}
