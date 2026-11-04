package cn.iocoder.yudao.server.deployment;

import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Optional server-release adapter. No changes to the upstream file implementation. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "npdms.server-deployment.enabled", havingValue = "true")
public class ServerSdpStorageDeploymentConfiguration {
    static final String KEY_REF = "credential:minio_app_user";
    static final String SECRET_REF = "credential:minio_app_password";

    @Bean
    static BeanPostProcessor deploymentFileCredentials(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if ("fileClientFactory".equals(name) && bean instanceof FileClientFactory factory) {
                    return new CredentialFactory(factory, environment);
                }
                return bean;
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner deploymentMinioInitializer(FileConfigService configs, Environment environment) {
        return args -> initialize(configs, environment);
    }

    static void initialize(FileConfigService configs, Environment environment) throws Exception {
        // Fail before creating configuration if credentials are absent.
        required(environment, "minio_app_user");
        required(environment, "minio_app_password");
        String name = "sdp-server-minio-" + environment.getRequiredProperty("SERVER_IP");
        String endpoint = environment.getRequiredProperty("npdms.server-deployment.minio-endpoint");
        String bucket = required(environment, "npdms.server-deployment.minio-bucket");
        if (!bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) {
            throw new IllegalStateException("Invalid deployment MinIO bucket");
        }
        String publicDomain = environment.getRequiredProperty("npdms.server-deployment.storage-public-origin") + "/" + bucket;
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("endpoint", endpoint);
        expected.put("domain", publicDomain);
        expected.put("bucket", bucket);
        expected.put("accessKey", KEY_REF);
        expected.put("accessSecret", SECRET_REF);
        expected.put("enablePathStyleAccess", true);
        expected.put("enablePublicAccess", false);
        expected.put("region", "us-east-1");

        var query = new FileConfigPageReqVO();
        query.setName(name);
        query.setPageSize(200);
        var page = configs.getFileConfigPage(query);
        if (page.getTotal() > 200) {
            throw new IllegalStateException("Ambiguous deployment file configurations");
        }
        var matching = page.getList().stream().filter(c -> name.equals(c.getName())).toList();
        if (matching.size() > 1) {
            throw new IllegalStateException("Duplicate deployment file configurations");
        }
        Long id;
        boolean created = false;
        if (matching.isEmpty()) {
            var request = new FileConfigSaveReqVO();
            request.setName(name);
            request.setStorage(20);
            request.setRemark("Server deployment: private MinIO; credentials resolved at runtime");
            request.setConfig(expected);
            id = configs.createFileConfig(request);
            created = true;
        } else {
            FileConfigDO current = matching.getFirst();
            if (!Integer.valueOf(20).equals(current.getStorage())
                    || !(current.getConfig() instanceof S3FileClientConfig c)
                    || !endpoint.equals(c.getEndpoint())
                    || !publicDomain.equals(c.getDomain())
                    || !bucket.equals(c.getBucket())
                    || !KEY_REF.equals(c.getAccessKey()) || !SECRET_REF.equals(c.getAccessSecret())
                    || !Boolean.TRUE.equals(c.getEnablePathStyleAccess())
                    || !Boolean.FALSE.equals(c.getEnablePublicAccess())
                    || !"us-east-1".equals(c.getRegion())) {
                throw new IllegalStateException("Deployment MinIO configuration was changed; refusing to overwrite it");
            }
            id = current.getId();
        }

        try {
            FileClient existingMaster = null;
            if (!created) {
                // The Owner resolves the selected master and rejects ambiguous selections.
                // A valid existing choice need not be the deployment MinIO configuration.
                try {
                    existingMaster = configs.getMasterFileClient();
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Existing master file storage is unavailable or ambiguous; configuration preserved", failure);
                }
                if (existingMaster == null || existingMaster.getId() == null) {
                    throw new IllegalStateException("Existing master file storage is missing; configuration preserved");
                }
            }
            verifyStorage(configs.getFileClient(id));
            if (created) {
                configs.updateFileConfigMaster(id);
            } else if (!id.equals(existingMaster.getId())) {
                verifyStorage(existingMaster);
            }
        } catch (Exception failure) {
            // Only the record created by this failed first attempt can be removed.
            // Existing configuration and any selected master remain Owner protected.
            if (created) {
                try {
                    configs.deleteFileConfig(id);
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        String releaseId = "ready";
        Files.writeString(Path.of(required(environment, "npdms.server-deployment.ready-file")), releaseId);
    }

    static void verifyStorage(FileClient client) throws Exception {
        if (client == null || client.getId() == null) {
            throw new IllegalStateException("File storage client is unavailable; configuration preserved");
        }
        byte[] content = "npdms-application-storage-check".getBytes(StandardCharsets.UTF_8);
        String key = ".application-check/" + UUID.randomUUID() + ".txt";
        client.upload(content, key, "text/plain");
        try {
            if (!Arrays.equals(content, client.getContent(key))) {
                throw new IllegalStateException("File storage readback mismatch: " + client.getId());
            }
        } finally {
            client.delete(key);
        }
    }

    static String required(Environment environment, String property) {
        String value = environment.getRequiredProperty(property);
        if (value.isBlank()) {
            throw new IllegalStateException("Empty deployment credential/property: " + property);
        }
        return value;
    }

    static final class CredentialFactory implements FileClientFactory {
        private final FileClientFactory delegate;
        private final Environment environment;

        CredentialFactory(FileClientFactory delegate, Environment environment) {
            this.delegate = delegate;
            this.environment = environment;
        }

        @Override
        public FileClient getFileClient(Long id) {
            return delegate.getFileClient(id);
        }

        @Override
        public <C extends FileClientConfig> void createOrUpdateFileClient(Long id, Integer storage, C config) {
            if (config instanceof S3FileClientConfig c
                    && (KEY_REF.equals(c.getAccessKey()) || SECRET_REF.equals(c.getAccessSecret()))) {
                if (!KEY_REF.equals(c.getAccessKey()) || !SECRET_REF.equals(c.getAccessSecret())) {
                    throw new IllegalStateException("Incomplete deployment credential references");
                }
                var resolved = new RedactedS3Config();
                resolved.setEndpoint(c.getEndpoint());
                resolved.setDomain(c.getDomain());
                resolved.setBucket(c.getBucket());
                resolved.setEnablePathStyleAccess(c.getEnablePathStyleAccess());
                resolved.setEnablePublicAccess(c.getEnablePublicAccess());
                resolved.setRegion(c.getRegion());
                resolved.setAccessKey(required(environment, "minio_app_user"));
                resolved.setAccessSecret(required(environment, "minio_app_password"));
                delegate.createOrUpdateFileClient(id, storage, resolved);
            } else {
                delegate.createOrUpdateFileClient(id, storage, config);
            }
        }
    }

    // Upstream client init/refresh logs config.toString(); never log resolved credentials.
    static final class RedactedS3Config extends S3FileClientConfig {
        @Override
        public String toString() {
            return "ServerMinioConfig[credentials redacted]";
        }
    }
}
