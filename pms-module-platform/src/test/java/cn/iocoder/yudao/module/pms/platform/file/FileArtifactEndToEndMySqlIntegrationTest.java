package cn.iocoder.yudao.module.pms.platform.file;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * F-PLT-001 文件主线的真实 MySQL 聚合验收入口。
 *
 * <p>复用各 Task 已建立的真实服务装配和数据夹具，避免复制后形成第二套实现事实。</p>
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class FileArtifactEndToEndMySqlIntegrationTest {

    @Nested
    class UploadAndVersionFlow extends FileUploadMySqlIntegrationTest {
    }

    @Nested
    class StorageCompensationFlow extends FileUploadCompensationMySqlIntegrationTest {
    }

    @Nested
    class QueryAndAccessFlow extends FileQueryAndAccessMySqlIntegrationTest {
    }

    @Nested
    class LifecycleFlow extends FileLifecycleMySqlIntegrationTest {
    }
}
