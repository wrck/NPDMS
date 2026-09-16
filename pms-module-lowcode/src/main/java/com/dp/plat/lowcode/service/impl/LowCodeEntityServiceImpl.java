package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.dto.DdlResultDTO;
import com.dp.plat.lowcode.engine.DdlGenerator;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import com.dp.plat.lowcode.mapper.LowCodeEntityMapper;
import com.dp.plat.lowcode.mapper.LowCodeFieldMapper;
import com.dp.plat.lowcode.mapper.LowCodeRelationMapper;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 低代码实体管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class LowCodeEntityServiceImpl extends ServiceImpl<LowCodeEntityMapper, LowCodeEntity>
        implements LowCodeEntityService {

    private final LowCodeFieldMapper fieldMapper;
    private final LowCodeRelationMapper relationMapper;
    private final DdlGenerator ddlGenerator;
    private final LowCodeConfigVersionService configVersionService;
    private final com.dp.plat.lowcode.engine.ddl.DdlExecutionService ddlExecutionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeEntity saveDesign(EntityDesignDTO design) {
        LowCodeEntity entity = design.getEntity();

        // 校验表名唯一
        if (isTableNameExists(entity.getTableName(), entity.getId())) {
            throw new IllegalArgumentException("物理表名已存在: " + entity.getTableName());
        }

        // 保存实体
        save(entity);

        // 保存字段（先删后插，便于更新）
        if (entity.getId() != null) {
            fieldMapper.delete(new LambdaQueryWrapper<LowCodeField>()
                    .eq(LowCodeField::getEntityId, entity.getId()));
        }
        for (LowCodeField field : design.getFields()) {
            field.setEntityId(entity.getId());
            fieldMapper.insert(field);
        }

        // 保存关联（先删后插）
        if (entity.getId() != null) {
            relationMapper.delete(new LambdaQueryWrapper<LowCodeRelation>()
                    .eq(LowCodeRelation::getFromEntityId, entity.getId()));
        }
        if (design.getRelations() != null) {
            for (LowCodeRelation relation : design.getRelations()) {
                relation.setFromEntityId(entity.getId());
                relationMapper.insert(relation);
            }
        }

        return entity;
    }

    @Override
    public EntityDesignDTO getDesign(Long entityId) {
        LowCodeEntity entity = getById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }

        List<LowCodeField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<LowCodeField>()
                        .eq(LowCodeField::getEntityId, entityId)
                        .orderByAsc(LowCodeField::getSortOrder));

        List<LowCodeRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<LowCodeRelation>()
                        .eq(LowCodeRelation::getFromEntityId, entityId));

        EntityDesignDTO dto = new EntityDesignDTO();
        dto.setEntity(entity);
        dto.setFields(fields);
        dto.setRelations(relations);
        return dto;
    }

    @Override
    public DdlResultDTO generateDdl(Long entityId) {
        EntityDesignDTO design = getDesign(entityId);

        List<String> statements = new ArrayList<>();
        // 构建 toEntityId → 物理表名 映射，供外键目标表名推导使用
        Map<Long, String> entityIdToTableName = buildEntityIdToTableName(design.getRelations());
        String createTableSql = ddlGenerator.generateCreateTable(
                design.getEntity(), design.getFields(), design.getRelations(), entityIdToTableName);
        statements.add(createTableSql);

        // 处理多对多中间表
        boolean hasJunction = false;
        String junctionDdl = null;
        if (design.getRelations() != null) {
            for (LowCodeRelation relation : design.getRelations()) {
                if ("MANY_TO_MANY".equals(relation.getRelationType())
                        && StringUtils.hasText(relation.getJunctionTable())) {
                    hasJunction = true;
                    // 查询目标实体表名（简化：通过 toEntityId 查询）
                    LowCodeEntity toEntity = getById(relation.getToEntityId());
                    junctionDdl = ddlGenerator.generateJunctionTable(
                            relation.getJunctionTable(),
                            design.getEntity().getTableName(),
                            toEntity.getTableName(),
                            relation.getFromFieldName(),
                            relation.getToFieldName(),
                            relation.getOnDelete());
                    statements.add(junctionDdl);
                    break;
                }
            }
        }

        return DdlResultDTO.builder()
                .tableName(design.getEntity().getTableName())
                .ddlStatements(statements)
                .hasJunctionTable(hasJunction)
                .junctionTableDdl(junctionDdl)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeEntity publish(Long entityId, String changeLog) {
        LowCodeEntity entity = getById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }

        // 1. 生成版本快照（保留原有逻辑）
        EntityDesignDTO design = getDesign(entityId);
        try {
            String snapshot = objectMapper.writeValueAsString(design);
            configVersionService.createSnapshot(
                    buildSnapshotContext("ENTITY", entityId, entity.getCode(), snapshot, changeLog));
        } catch (Exception e) {
            throw new RuntimeException("生成版本快照失败", e);
        }

        // 2. 执行 DDL
        if ("DRAFT".equals(entity.getStatus())) {
            // 首次发布：CREATE TABLE
            ddlExecutionService.executeCreate(entityId, false);
        } else {
            // 已发布：增量 ALTER（不自动 DROP COLUMN）
            ddlExecutionService.executeAlter(entityId, false);
        }

        // 3. 更新状态
        entity.setStatus("PUBLISHED");
        updateById(entity);
        return entity;
    }

    @Override
    public boolean isTableNameExists(String tableName, Long excludeId) {
        LambdaQueryWrapper<LowCodeEntity> wrapper = new LambdaQueryWrapper<LowCodeEntity>()
                .eq(LowCodeEntity::getTableName, tableName);
        if (excludeId != null) {
            wrapper.ne(LowCodeEntity::getId, excludeId);
        }
        return baseMapper.selectOne(wrapper) != null;
    }

    private LowCodeConfigVersionService.SnapshotContext buildSnapshotContext(
            String type, Long id, String code, String snapshot, String changeLog) {
        return new LowCodeConfigVersionService.SnapshotContext(
                type, id, code, snapshot, changeLog);
    }

    /**
     * 根据关联列表构建 toEntityId → 物理表名 映射，用于外键目标表名推导。
     * 自关联（fromEntityId == toEntityId）跳过（由生成器用当前表名处理）。
     */
    private Map<Long, String> buildEntityIdToTableName(List<LowCodeRelation> relations) {
        Map<Long, String> result = new HashMap<>();
        if (relations == null || relations.isEmpty()) {
            return result;
        }
        List<Long> targetIds = new ArrayList<>();
        for (LowCodeRelation rel : relations) {
            if (rel.getToEntityId() == null) {
                continue;
            }
            if (rel.getFromEntityId() != null && rel.getFromEntityId().equals(rel.getToEntityId())) {
                continue;
            }
            if (!targetIds.contains(rel.getToEntityId())) {
                targetIds.add(rel.getToEntityId());
            }
        }
        if (targetIds.isEmpty()) {
            return result;
        }
        List<LowCodeEntity> targets = listByIds(targetIds);
        if (targets != null) {
            for (LowCodeEntity t : targets) {
                if (t.getTableName() != null) {
                    result.put(t.getId(), t.getTableName());
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LowCodeRelation> saveRelations(Long entityId, List<LowCodeRelation> relations) {
        // 先删旧
        relationMapper.delete(new LambdaQueryWrapper<LowCodeRelation>()
                .eq(LowCodeRelation::getFromEntityId, entityId));
        // 再插新
        if (relations != null) {
            for (LowCodeRelation rel : relations) {
                rel.setFromEntityId(entityId);
                rel.setId(null); // 确保新增
                relationMapper.insert(rel);
            }
        }
        return relationMapper.selectList(new LambdaQueryWrapper<LowCodeRelation>()
                .eq(LowCodeRelation::getFromEntityId, entityId));
    }
}
