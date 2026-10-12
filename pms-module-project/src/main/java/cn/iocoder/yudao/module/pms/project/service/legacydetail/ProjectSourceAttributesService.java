package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.ProjectMasterSyncProofApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.ProjectSourcesQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads formal project facts; reconciliation never writes master attributes or source mirrors. */
@Service
@RequiredArgsConstructor
public class ProjectSourceAttributesService {
    private final ProjectManualCreationService access;
    private final ProjectMasterMapper projects;
    private final ProjectHeaderFinalCustomerService finalCustomers;
    private final ProjectMasterSyncProofApi syncProof;
    private final ProjectLegacyDetailMapper sources;
    public static final List<String> FORMAL_FIELDS=List.of(
            "projectType","projectCode","projectName","lifecycleStatus","currentStage","closedAt",
            "departmentCode","companyId","companyCode","companyName","marketName","systemName","expendName","industryName",
            "notTrackReason","orderCreateTime","projectCategory","signingMethod","implementationMode",
            "customerProjectName","salesType","majorProjectLevel","creator","createTime","updater","updateTime",
            "effectiveFrom","effectiveTo","disabled","projectStartTime","projectRefreshTime","projectCloseTime","customInfo");

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public NativeEntityMigrationRow importRecord(Long projectId,ProjectLegacyRecordDO record,ProjectAccessActor actor) {
        validateActor(actor);access.getProjectForManage(projectId,actor);
        var row=projects.selectById(projectId);validateProject(row,actor);
        Map<String,String> original=JsonUtils.parseObject(record.getPayloadJson(),new TypeReference<Map<String,String>>() {});
        if (!"pm_project_header".equals(record.getSourceTable()) || record.getSourceKey()==null
                || original==null || !Objects.equals(record.getSourceKey(),original.get("projectId")))
            throw new IllegalArgumentException("DPPMS_NATIVE_PROJECT_SOURCE_IDENTITY_INVALID");
        var proofs=syncProof.verify(new ProjectMasterSyncProofApi.Query(actor.tenantId(),actor.actorId(),List.of(record.getSourceKey())));
        if (proofs.size()!=1 || !Objects.equals(proofs.getFirst().sourceKey(),record.getSourceKey())
                || !Objects.equals(proofs.getFirst().targetId(),projectId)
                || !ProjectMasterMigrationService.sameOriginal(original,proofs.getFirst().originalFields()))
            throw new IllegalStateException("DPPMS_PROJECT_MASTER_SYNC_PROOF_MISMATCH");
        var party=finalCustomers.importHeader(projectId,record,actor);
        var project=new ExternalTargetMapping("PROJ","Project","proj_project",projectId,"PROJECT_ENTITY",0);
        return party==null?NativeEntityMigrationRow.mapped("project",record.getSourceKey(),project)
                :NativeEntityMigrationRow.mapped("project",record.getSourceKey(),project,party);
    }

    public Map<String,Object> readAttributes(Long projectId,ProjectAccessActor actor) {
        validateActor(actor);access.getProject(projectId,actor);
        var row=projects.selectById(projectId);validateProject(row,actor);
        var bean=new BeanWrapperImpl(row);Map<String,Object> values=new LinkedHashMap<>();
        FORMAL_FIELDS.forEach(field->values.put(field,bean.getPropertyValue(field)));
        values.put("finalCustomerNames",finalCustomers.readNames(projectId,actor));
        return values;
    }
    public String readSourceKey(Long projectId,ProjectAccessActor actor) {
        validateActor(actor);access.getProject(projectId,actor);
        var linked=sources.selectProjectSources(new ProjectSourcesQuery(actor.tenantId(),projectId));
        return linked.size()==1?linked.getFirst().getSourceProjectKey():null;
    }
    private static void validateActor(ProjectAccessActor actor) {
        if (actor==null || actor.actorId()==null || actor.actorId()<=0
                || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_NATIVE_PROJECT_SOURCE_FORBIDDEN");
    }
    private static void validateProject(ProjectMasterDO row,ProjectAccessActor actor) {
        if (row==null || !Objects.equals(row.getTenantId(),actor.tenantId()) || Boolean.TRUE.equals(row.getDeleted()))
            throw new IllegalArgumentException("DPPMS_NATIVE_PROJECT_SOURCE_FORBIDDEN");
    }
}
