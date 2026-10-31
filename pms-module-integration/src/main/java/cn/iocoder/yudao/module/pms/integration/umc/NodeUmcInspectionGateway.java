package cn.iocoder.yudao.module.pms.integration.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Independent protocol provider. Business authorization, durable operation/checkpoint storage and
 * PLT registration remain with the caller. One configured UMC account is serialized in this JVM. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "pms.integration.umc", name = "provider", havingValue = "node")
public class NodeUmcInspectionGateway implements UmcInspectionApi {
    private static final long MAX_BYTES = 52_428_800L;
    private final UmcInspectionProperties properties;

    public NodeUmcInspectionGateway(UmcInspectionProperties properties) { this.properties = properties; }
    private record FileInput(String name, String path) { }
    private record WorkerInput(Scope scope, String requestId, List<FileInput> files,
                               String outputDirectory, Checkpoint checkpoint, boolean queryOnly) { }
    private record WorkerEvent(String kind, String code, Checkpoint checkpoint, String fileName,
                               String docSha256, String detailSha256, List<String> warnings) { }

    @Override
    public synchronized Result inspect(Request request) {
        validate(request);
        if (blank(properties.getBaseUrl()) || blank(properties.getUsername()) || blank(properties.getPassword())
                || blank(properties.getNodeExecutable()) || properties.getTimeoutSeconds() < 1
                || properties.getTimeoutSeconds() > 1800) throw new Failure("NOT_CONFIGURED", request.checkpoint());
        Path work = null;
        Process process = null;
        var checkpoint = new AtomicReference<>(request.checkpoint());
        try {
            work = Files.createTempDirectory("npdms-umc-").toRealPath();
            Path worker = work.resolve("inspection-worker.cjs");
            try (var resource = getClass().getResourceAsStream("/umc/inspection-worker.cjs")) {
                if (resource == null) throw new Failure("WORKER_UNAVAILABLE", checkpoint.get());
                Files.copy(resource, worker);
            }
            List<FileInput> files = new ArrayList<>();
            for (int i = 0; i < request.logs().size(); i++) {
                var log = request.logs().get(i);
                var file = work.resolve("input-" + i);
                Files.write(file, log.content());
                files.add(new FileInput(log.fileName(), file.toString()));
            }
            var builder = new ProcessBuilder(properties.getNodeExecutable(), "--max-old-space-size=256", worker.toString());
            builder.directory(work.toFile());
            var env = builder.environment();
            configureEnvironment(env, properties);
            process = builder.start();
            env.remove("UMC_PASS");
            var owned = process;
            try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
                try {
                    var events = readers.submit(() -> readEvents(owned, checkpoint));
                    var errors = readers.submit(() -> {
                        // Drain bounded chunks without retaining or logging server/credential material.
                        try (var stream = owned.getErrorStream()) { var buffer = new byte[4096]; while (stream.read(buffer) != -1) { } }
                        return null;
                    });
                    try (var stdin = process.getOutputStream()) {
                        stdin.write(JsonUtils.toJsonString(new WorkerInput(request.scope(), request.requestId(), files,
                                work.toString(), request.checkpoint(), request.queryOnly())).getBytes(StandardCharsets.UTF_8));
                    }
                    if (!process.waitFor(properties.getTimeoutSeconds() + 8L, TimeUnit.SECONDS)) {
                        process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS);
                        throw new Failure("PROCESS_TIMEOUT", uncertainCheckpoint(checkpoint.get()));
                    }
                    var event = events.get(3, TimeUnit.SECONDS);
                    errors.get(3, TimeUnit.SECONDS);
                    if (event == null || process.exitValue() != 0 || !"result".equals(event.kind())) {
                        if (event != null && "error".equals(event.kind()) && safeCode(event.code()))
                            throw new Failure(event.code(), event.checkpoint());
                        throw new Failure("PROCESS_FAILED", uncertainCheckpoint(checkpoint.get()));
                    }
                    if (event.checkpoint() == null || event.checkpoint().taskId() == null || event.checkpoint().reportId() == null)
                        throw new Failure("INVALID_RESULT", checkpoint.get());
                    byte[] doc = read(work.resolve("report.doc")), rawDetail = read(work.resolve("detail.json"));
                    if (!sha(doc).equals(event.docSha256()) || !sha(rawDetail).equals(event.detailSha256()))
                        throw new Failure("RESULT_HASH_MISMATCH", checkpoint.get());
                    try { validateWordXml(doc); }
                    catch (Exception invalid) { throw new Failure("INVALID_DOC", checkpoint.get()); }
                    var detail = JsonUtils.parseObject(rawDetail, Report.class);
                    if (detail == null || !Objects.equals(detail.id(), event.checkpoint().reportId())
                            || !Objects.equals(detail.taskid(), event.checkpoint().taskId()) || detail.devlist() == null)
                        throw new Failure("DETAIL_IDENTITY_MISMATCH", checkpoint.get());
                    return new Result(event.checkpoint(), event.fileName(), "application/vnd.ms-wordml", event.docSha256(),
                            doc, event.detailSha256(), rawDetail, detail, event.warnings());
                } finally { if (owned.isAlive()) owned.destroyForcibly(); }
            }
        } catch (Failure failure) { throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new Failure("INTERRUPTED", uncertainCheckpoint(checkpoint.get()));
        } catch (Exception failure) {
            throw new Failure(process == null ? "WORKER_START_FAILED" : "ADAPTER_FAILED",
                    process == null ? checkpoint.get() : uncertainCheckpoint(checkpoint.get()));
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (work != null) {
                try (var paths = Files.walk(work)) {
                    for (var file : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                } catch (Exception ignored) { /* no credentials are stored in work; retain no secret-bearing cause */ }
            }
        }
    }

    /** Host/process failures cannot prove that the last observed pre-upload checkpoint is still current. */
    static Checkpoint uncertainCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null || !"NOT_SENT".equals(checkpoint.uploadState())) return checkpoint;
        return new Checkpoint(checkpoint.inputDigest(), checkpoint.taskId(), checkpoint.reportId(), "UNKNOWN");
    }

    private WorkerEvent readEvents(Process process, AtomicReference<Checkpoint> checkpoint) throws Exception {
        WorkerEvent terminal = null;
        try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line; int bytes = 0;
            while ((line = lines.readLine()) != null) {
                bytes += line.length();
                if (bytes > 262144) { process.destroyForcibly(); throw new Failure("WORKER_OUTPUT_LIMIT", checkpoint.get()); }
                var event = JsonUtils.parseObjectQuietly(line, WorkerEvent.class);
                if (event == null) throw new Failure("INVALID_WORKER_EVENT", checkpoint.get());
                if (event.checkpoint() != null) checkpoint.set(event.checkpoint());
                if ("result".equals(event.kind()) || "error".equals(event.kind())) terminal = event;
            }
        }
        return terminal;
    }

    static void configureEnvironment(Map<String, String> environment, UmcInspectionProperties properties) {
        var allowed = Set.of("PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "HOME", "USERPROFILE",
                "APPDATA", "LOCALAPPDATA", "LANG", "LC_ALL", "NODE_EXTRA_CA_CERTS", "SSL_CERT_FILE", "SSL_CERT_DIR");
        environment.keySet().removeIf(key -> !allowed.contains(key.toUpperCase(Locale.ROOT)));
        environment.put("UMC_URL", properties.getBaseUrl()); environment.put("UMC_USER", properties.getUsername());
        environment.put("UMC_PASS", properties.getPassword());
        environment.put("UMC_TIMEOUT_SECONDS", Integer.toString(properties.getTimeoutSeconds()));
        if (!blank(properties.getCertificateSha256())) environment.put("UMC_CERT_SHA256", properties.getCertificateSha256());
    }

    private static void validate(Request request) {
        if (request == null || request.scope() == null || request.scope().tenantId() == null
                || !Objects.equals(request.scope().tenantId(), TenantContextHolder.getRequiredTenantId())
                || blank(request.scope().taskKey()) || blank(request.scope().attemptKey())
                || blank(request.requestId()) || request.requestId().length() > 128
                || request.logs() == null || request.logs().isEmpty() || request.logs().size() > 64)
            throw new Failure("INVALID_INPUT", request == null ? null : request.checkpoint());
        long total = 0;
        for (var log : request.logs()) {
            if (log == null || blank(log.fileName()) || log.fileName().length() > 255
                    || log.sizeBytes() == 0 || (total += log.sizeBytes()) > MAX_BYTES)
                throw new Failure("INVALID_INPUT", request.checkpoint());
        }
    }
    private static byte[] read(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) == 0 || Files.size(path) > MAX_BYTES) throw new Exception();
        return Files.readAllBytes(path);
    }
    static void validateWordXml(byte[] bytes) throws Exception {
        var factory = javax.xml.parsers.SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        reader.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("Invalid WordML");
            }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("Invalid WordML");
            }
        });
        reader.setContentHandler(new org.xml.sax.helpers.DefaultHandler() {
            private boolean root;
            @Override public void startElement(String uri, String local, String qualified, org.xml.sax.Attributes attributes)
                    throws org.xml.sax.SAXException {
                if (!root && (!"wordDocument".equals(local)
                        || !"http://schemas.microsoft.com/office/word/2003/wordml".equals(uri)))
                    throw new org.xml.sax.SAXException("Invalid WordML root");
                root = true;
            }
        });
        reader.parse(new org.xml.sax.InputSource(new java.io.ByteArrayInputStream(bytes)));
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static boolean safeCode(String code) { return code != null && code.matches("[A-Z][A-Z0-9_]{2,80}"); }
}
