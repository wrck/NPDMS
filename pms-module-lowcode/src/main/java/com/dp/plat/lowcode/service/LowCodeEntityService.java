package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.dto.DdlResultDTO;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeRelation;

import java.util.List;

/**
 * 低代码实体管理服务。
 */
public interface LowCodeEntityService extends IService<LowCodeEntity> {

    /**
     * 保存完整实体设计（实体 + 字段 + 关联）。
     */
    LowCodeEntity saveDesign(EntityDesignDTO design);

    /**
     * 查询完整实体设计（含字段和关联）。
     */
    EntityDesignDTO getDesign(Long entityId);

    /**
     * 生成 DDL 语句（不执行）。
     */
    DdlResultDTO generateDdl(Long entityId);

    /**
     * 发布实体（DRAFT → PUBLISHED），生成版本快照。
     */
    LowCodeEntity publish(Long entityId, String changeLog);

    /**
     * 校验表名唯一性。
     */
    boolean isTableNameExists(String tableName, Long excludeId);

    /**
     * 保存实体关联
     * @param entityId 实体 ID
     * @param relations 关联列表
     * @return 保存后的关联列表
     */
    List<LowCodeRelation> saveRelations(Long entityId, List<LowCodeRelation> relations);
}
