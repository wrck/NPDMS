package cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliverableChecklistMapper extends BaseMapperX<DeliverableChecklistDO> {

    @Insert("""
            INSERT IGNORE INTO acc_project_deliverable (
                tenant_id, project_id, template_requirement_key, source_template_revision_id,
                applicable_stage_code, required_flag, template_id, code, name,
                deliverable_type, status, version, deleted
            ) VALUES (
                #{entity.tenantId}, #{entity.projectId}, #{entity.templateRequirementKey},
                #{entity.sourceTemplateRevisionId}, #{entity.applicableStageCode}, #{entity.requiredFlag},
                #{entity.templateId}, #{entity.code}, #{entity.name}, #{entity.deliverableType},
                #{entity.status}, 0, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "entity.id")
    int insertInitializationIgnore(@Param("entity") DeliverableChecklistDO entity);

    default DeliverableChecklistDO selectByInitializationKey(Long tenantId, Long projectId,
                                                              Long templateRevisionId, String requirementKey) {
        return selectOne(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getTenantId, tenantId)
                .eq(DeliverableChecklistDO::getProjectId, projectId)
                .eq(DeliverableChecklistDO::getSourceTemplateRevisionId, templateRevisionId)
                .eq(DeliverableChecklistDO::getTemplateRequirementKey, requirementKey));
    }

    default DeliverableChecklistDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getProjectId, projectId)
                .eq(DeliverableChecklistDO::getCode, code));
    }

    default PageResult<DeliverableChecklistDO> selectPage(DeliverableChecklistPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eqIfPresent(DeliverableChecklistDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(DeliverableChecklistDO::getCode, reqVO.getCode())
                .likeIfPresent(DeliverableChecklistDO::getName, reqVO.getName())
                .eqIfPresent(DeliverableChecklistDO::getAcceptanceId, reqVO.getAcceptanceId())
                .eqIfPresent(DeliverableChecklistDO::getDeliverableType, reqVO.getDeliverableType())
                .eqIfPresent(DeliverableChecklistDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeliverableChecklistDO::getId));
    }

    /**
     * 查询某验收下指定类型的交付件列表（FR-ACC-005 门禁校验数据源）
     */
    default List<DeliverableChecklistDO> selectListByAcceptanceIdAndType(Long acceptanceId, String deliverableType) {
        return selectList(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getAcceptanceId, acceptanceId)
                .eq(DeliverableChecklistDO::getDeliverableType, deliverableType));
    }

}
