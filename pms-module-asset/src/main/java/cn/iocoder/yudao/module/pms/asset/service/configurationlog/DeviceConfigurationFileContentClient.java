package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class DeviceConfigurationFileContentClient {

    private static final long MAX_CONTENT_LENGTH = 20L * 1024L * 1024L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public DeviceConfigurationFileContentClient() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    DeviceConfigurationFileContentClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public InputStream open(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("配置Log文件地址协议不受支持");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("配置Log文件读取失败");
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_CONTENT_LENGTH) {
                response.body().close();
                throw new IllegalStateException("配置Log文件超过大小限制");
            }
            return new BoundedInputStream(response.body(), MAX_CONTENT_LENGTH);
        } catch (IOException ex) {
            throw new IllegalStateException("配置Log文件读取失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("配置Log文件读取被中断", ex);
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long readBytes;

        private BoundedInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                addReadBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                addReadBytes(count);
            }
            return count;
        }

        private void addReadBytes(long count) throws IOException {
            readBytes += count;
            if (readBytes > maxBytes) {
                close();
                throw new IOException("配置Log文件超过大小限制");
            }
        }
    }
}
