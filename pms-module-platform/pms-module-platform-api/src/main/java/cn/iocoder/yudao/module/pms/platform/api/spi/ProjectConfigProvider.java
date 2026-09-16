package cn.iocoder.yudao.module.pms.platform.api.spi;

/**
 * 项目配置读取 SPI（迁移自源工程 pms-common com.dp.plat.common.spi.ProjectConfigProvider，
 * TD-P8-001 配套）。
 *
 * <p>用于在 pms-module-workflow 等基础设施模块中读取项目级配置，
 * 而无需直接依赖 pms-module-project 模块（避免双向依赖环）。</p>
 *
 * <p>由 pms-module-project 模块实现并注册为 Spring Bean，
 * 其他模块通过 {@code @Autowired(required=false)} 注入。</p>
 *
 * <p>配置读取顺序：项目级 &gt; 模板级 &gt; 系统默认。</p>
 */
public interface ProjectConfigProvider {

    /**
     * 读取字符串配置值。
     *
     * @param projectId  项目ID（NULL 表示无项目级）
     * @param templateId 模板ID（NULL 表示无模板级）
     * @param key        配置键
     * @return 配置值，找不到返回 NULL
     */
    String get(Long projectId, Long templateId, String key);
}