package cn.iocoder.yudao.module.pms.platform.testassembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 统一业务模型测试装配应用：只装配 system/infra/platform 与两个测试实体，
 * 不装配 pms-module-project 等具体项目运行时；用于 P04 真实浏览器独立办理闭环。
 * 测试实体不进入生产菜单、字典或业务范围；表结构在启动时按需创建。
 */
@SpringBootApplication(scanBasePackages = "cn.iocoder.yudao.module")
public class TestAssemblyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestAssemblyApplication.class, args);
    }

    @Bean
    ApplicationRunner demoTableInitializer(JdbcTemplate jdbcTemplate) {
        return arguments -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pms_plat_demo_notice (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        content TEXT,
                        level INT,
                        pinned BIT(1),
                        published_at DATE,
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        version BIGINT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) DEFAULT '',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64) DEFAULT '',
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted BIT(1) NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pms_plat_demo_memo (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        subject VARCHAR(255) NOT NULL,
                        detail TEXT,
                        due_date DATE,
                        archived BIT(1),
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        version BIGINT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) DEFAULT '',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64) DEFAULT '',
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted BIT(1) NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pms_plat_demo_ticket (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        summary VARCHAR(255) NOT NULL,
                        detail TEXT,
                        priority INT,
                        handled BIT(1),
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        version BIGINT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) DEFAULT '',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64) DEFAULT '',
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted BIT(1) NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pms_plat_demo_requisition (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        quantity INT,
                        reason TEXT,
                        urgent BIT(1),
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        version BIGINT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) DEFAULT '',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64) DEFAULT '',
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted BIT(1) NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pms_plat_demo_requisition_revision (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        quantity INT,
                        reason TEXT,
                        urgent BIT(1),
                        entity_id BIGINT NOT NULL,
                        revision_no INT NOT NULL,
                        source_revision_id BIGINT,
                        base_effective_revision_id BIGINT,
                        base_entity_version INT,
                        change_reason VARCHAR(255),
                        frozen_by BIGINT,
                        frozen_at DATETIME,
                        revision_state VARCHAR(32),
                        effective BIT(1) NOT NULL DEFAULT 0,
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        version BIGINT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) DEFAULT '',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64) DEFAULT '',
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted BIT(1) NOT NULL DEFAULT 0
                    )
                    """);
        };
    }
}
