package cn.iocoder.yudao.server.deployment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exercises startup configuration and failure protection without a database or network. */
public class StorageDeploymentProbe {
    static final class Fixture {
        final Map<String, Object> properties = new HashMap<>(Map.of(
                "SERVER_IP", "192.0.2.1", "minio_app_user", "synthetic-user",
                "minio_app_password", "synthetic-secret", "npdms.server-deployment.minio-endpoint", "http://127.0.0.1:9000",
                "npdms.server-deployment.storage-public-origin", "https://storage.example.test",
                "npdms.server-deployment.minio-bucket", "custom-files"));
        final StandardEnvironment environment = new StandardEnvironment();
        final Map<String, byte[]> objects = new HashMap<>();
        FileConfigDO record;
        long master = 1L;
        int created;
        int deleted;
        int selected;
        boolean corruptRead;
        final FileClient client = (FileClient) Proxy.newProxyInstance(FileClient.class.getClassLoader(), new Class<?>[]{FileClient.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> 2L;
                    case "upload" -> { objects.put((String) args[1], (byte[]) args[0]); yield "unused"; }
                    case "getContent" -> corruptRead ? new byte[0] : objects.get((String) args[0]);
                    case "delete" -> { objects.remove((String) args[0]); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
        final FileConfigService service = (FileConfigService) Proxy.newProxyInstance(FileConfigService.class.getClassLoader(), new Class<?>[]{FileConfigService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFileConfigPage" -> new PageResult<FileConfigDO>(record == null ? List.of() : List.of(record), record == null ? 0L : 1L);
                    case "createFileConfig" -> {
                        var request = (FileConfigSaveReqVO) args[0];
                        var config = request.getConfig();
                        var s3 = new S3FileClientConfig();
                        s3.setBucket((String) config.get("bucket")); s3.setDomain((String) config.get("domain"));
                        s3.setEndpoint((String) config.get("endpoint")); s3.setRegion((String) config.get("region"));
                        s3.setAccessKey((String) config.get("accessKey")); s3.setAccessSecret((String) config.get("accessSecret"));
                        s3.setEnablePathStyleAccess((Boolean) config.get("enablePathStyleAccess"));
                        s3.setEnablePublicAccess((Boolean) config.get("enablePublicAccess"));
                        record = new FileConfigDO(); record.setId(2L); record.setName(request.getName());
                        record.setStorage(request.getStorage()); record.setConfig(s3); created++;
                        yield 2L;
                    }
                    case "getFileClient", "getMasterFileClient" -> client;
                    case "updateFileConfigMaster" -> { master = (Long) args[0]; selected++; yield null; }
                    case "deleteFileConfig" -> { check(args[0].equals(2L), "Historical configuration deleted"); deleted++; record = null; yield null; }
                    default -> throw new AssertionError("Unexpected mutation/call: " + method.getName());
                });

        Fixture(Path ready) {
            properties.put("npdms.server-deployment.ready-file", ready.toString());
            environment.getPropertySources().addFirst(new MapPropertySource("fixture", properties));
        }
        void run() throws Exception { ServerSdpStorageDeploymentConfiguration.initialize(service, environment); }
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void rejected(Fixture fixture) throws Exception {
        try { fixture.run(); throw new AssertionError("Invalid configuration accepted"); }
        catch (IllegalStateException expected) { }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Files.createDirectories(root);
        var valid = new Fixture(root.resolve("valid-ready")); valid.run();
        var config = (S3FileClientConfig) valid.record.getConfig();
        check("custom-files".equals(config.getBucket()), "Configured bucket ignored");
        check("https://storage.example.test/custom-files".equals(config.getDomain()), "Domain/bucket mismatch");
        check(valid.master == 2L && valid.created == 1 && valid.selected == 1, "New default not selected");
        check(valid.objects.isEmpty(), "Probe objects retained");
        valid.run(); check(valid.created == 1 && valid.selected == 1, "Repeated startup changed configuration");
        config.setBucket("npdms"); rejected(valid);
        check(valid.deleted == 0 && valid.selected == 1, "Existing configuration overwritten");

        var failure = new Fixture(root.resolve("failure-ready")); failure.corruptRead = true; rejected(failure);
        check(failure.master == 1L && failure.selected == 0 && failure.deleted == 1, "Failed probe changed old default");
        check(failure.objects.isEmpty() && !Files.exists(root.resolve("failure-ready")), "Failed probe declared ready");
        for (String bucket : List.of("", "../npdms", "SDP", "sdp/invalid")) {
            var invalid = new Fixture(root.resolve("invalid-ready"));
            invalid.properties.put("npdms.server-deployment.minio-bucket", bucket); rejected(invalid);
            check(invalid.created == 0, "Invalid bucket persisted");
        }
        var missing = new Fixture(root.resolve("missing-ready"));
        missing.properties.remove("npdms.server-deployment.minio-bucket"); rejected(missing);
        check(missing.created == 0, "Missing bucket persisted");
        System.out.println("PASS custom bucket/domain, default selection, repeat startup, existing drift refusal, failed read rollback, invalid/missing bucket refusal");
    }
}
