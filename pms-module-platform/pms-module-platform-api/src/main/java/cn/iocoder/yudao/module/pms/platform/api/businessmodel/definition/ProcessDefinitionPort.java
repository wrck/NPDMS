package cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition;

import java.util.List;
import java.util.Optional;

/**
 * 中性过程定义读取：模板配置、执行层与页面读取同一发布版本。
 * 只暴露已发布定义；草稿不经此端口对执行层可见。
 */
public interface ProcessDefinitionPort {

    Optional<ProcessDefinitionSnapshot> findPublished(String definitionCode);

    List<ProcessDefinitionSnapshot> listPublished(String ownerModule, String entityType);
}
