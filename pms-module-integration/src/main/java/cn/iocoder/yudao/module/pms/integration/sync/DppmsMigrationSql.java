package cn.iocoder.yudao.module.pms.integration.sync;

import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Editable initial SQL is copied into the versioned task; adapters never force this text at execution. */
final class DppmsMigrationSql {
    private DppmsMigrationSql() {}
    static String initial(String name) {
        try (var input = new ClassPathResource("migration/dppms/" + name + ".sql").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException error) {
            throw new IllegalStateException("缺少DPPMS整表任务的初始查询配置: " + name, error);
        }
    }
}
