package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileSecurityScanProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAvFileSecurityScanProvider implements FileSecurityScanProvider {

    private static final String PROVIDER_CODE = "CLAMAV";
    private static final int STREAM_CHUNK_BYTES = 64 * 1024;

    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public ClamAvFileSecurityScanProvider(
            @Value("${pms.file.scan.clamav.host:}") String host,
            @Value("${pms.file.scan.clamav.port:0}") int port,
            @Value("${pms.file.scan.clamav.connect-timeout-ms:3000}") int connectTimeoutMillis,
            @Value("${pms.file.scan.clamav.read-timeout-ms:30000}") int readTimeoutMillis) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public FileSecurityScanResult scan(FileSecurityScanCommand command) {
        if (!configured()) {
            return error("PROVIDER_NOT_CONFIGURED");
        }
        try {
            String version = queryVersion();
            String response = scanContent(command.validatedContent());
            if (response.endsWith(" OK")) {
                return new FileSecurityScanResult("PASSED", PROVIDER_CODE, version, null);
            }
            if (response.endsWith(" FOUND")) {
                return new FileSecurityScanResult("REJECTED", PROVIDER_CODE, version, "MALWARE_FOUND");
            }
            return new FileSecurityScanResult("ERROR", PROVIDER_CODE, version, "INVALID_SCAN_RESPONSE");
        } catch (IOException | RuntimeException ex) {
            return error("PROVIDER_UNAVAILABLE");
        }
    }

    private String queryVersion() throws IOException {
        try (Socket socket = connect()) {
            socket.getOutputStream().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String version = readNullTerminated(socket.getInputStream());
            if (version.isBlank()) {
                throw new IOException("empty ClamAV version");
            }
            return version;
        }
    }

    private String scanContent(byte[] content) throws IOException {
        try (Socket socket = connect();
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += STREAM_CHUNK_BYTES) {
                int length = Math.min(STREAM_CHUNK_BYTES, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
            }
            output.writeInt(0);
            output.flush();
            return readNullTerminated(socket.getInputStream());
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        socket.setSoTimeout(readTimeoutMillis);
        return socket;
    }

    private static String readNullTerminated(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream(128);
        int value;
        while ((value = input.read()) != -1 && value != 0) {
            response.write(value);
        }
        if (value != 0) {
            throw new IOException("incomplete ClamAV response");
        }
        return response.toString(StandardCharsets.US_ASCII).trim();
    }

    private boolean configured() {
        return !host.isBlank() && port > 0 && port <= 65_535
                && connectTimeoutMillis > 0 && readTimeoutMillis > 0;
    }

    private static FileSecurityScanResult error(String reasonCode) {
        return new FileSecurityScanResult("ERROR", PROVIDER_CODE, null, reasonCode);
    }
}
