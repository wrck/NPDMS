package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Ordinary entity integration only: site surveys do not implement or register a version provider. */
@Component
@RequiredArgsConstructor
public class SiteSurveyEntityProvider implements EntityFieldProvider {
    public static final EntityFieldMapping<SiteSurveyEntityDO> FIELDS = new EntityFieldMapping<>(SiteSurveyEntityDO.class);
    private final SiteSurveyEntityMapper mapper;
    private final SiteSurveyDetails details;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    @Override public String ownerModule() { return "SOL"; }
    @Override public String entityType() { return "SITE_SURVEY"; }
    /** Read-only catalog metadata and aggregate child facts share the actual typed model. */
    public static List<EntityField> publicFields() {
        Map<String,EntityField> fields=new LinkedHashMap<>();
        cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector.businessFields(SiteSurveyEntityDO.class)
                .forEach(field -> fields.put(field.code(),new EntityField(field.code(),field.type(),field.required())));
        FIELDS.fields().forEach(field -> fields.putIfAbsent(field.code(),field));
        return List.copyOf(fields.values());
    }
    public static List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor> modelFields() {
        return publicFields().stream().map(field -> new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor(
                field.code(),fieldName(field.code()),field.type(),field.required() || java.util.Set.of("name","code").contains(field.code()),
                true,!java.util.Set.of("projectId","code","status").contains(field.code()),null)).toList();
    }
    private static String fieldName(String code) {
        return switch(code) {
            case "projectId" -> "项目编号"; case "code" -> "工勘编码"; case "name" -> "工勘名称";
            case "surveyDate" -> "勘察日期"; case "surveyorUserId" -> "工勘责任人"; case "location" -> "位置";
            case "powerSupply" -> "供电"; case "cabinet" -> "机柜"; case "networkPort" -> "网络端口"; case "fiber" -> "光纤";
            case "module" -> "模块"; case "cable" -> "线缆"; case "ground" -> "接地"; case "constructionResource" -> "施工资源";
            case "conclusion" -> "工勘结论"; case "remark" -> "备注"; case "outsourceRequired" -> "是否委外"; case "status" -> "状态";
            case "cabinetReady" -> "机柜就绪"; case "cableReady" -> "线缆就绪"; case "moduleReady" -> "模块就绪";
            case "originalModule" -> "原厂模块"; case "manufacturerInstallation" -> "厂商安装"; case "railTrayRequired" -> "需要导轨托盘";
            case "materialMatches" -> "材料匹配"; case "requiredEndDate" -> "要求结束日期"; case "powerTypes" -> "电源类型";
            case "powerEnvironments" -> "供电环境"; case "networkPortTypes" -> "网络端口类型"; case "selectedMaterials" -> "选定材料";
            default -> code;
        };
    }
    @Override public List<EntityField> fields() { return publicFields(); }

    @Override
    public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        var row = authorized(target, actor, false);
        details.load(row);
        Map<String, EntityFieldValue> values = new LinkedHashMap<>();
        FIELDS.read(row).forEach((code, value) -> values.put(code, EntityFieldValue.known(value)));
        cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector.readValues(row,
                cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector.businessFields(SiteSurveyEntityDO.class))
                .forEach((code,value) -> values.put(code,EntityFieldValue.known(value)));
        return values;
    }

    @Override public Long concurrencyBasis(EntityDataRef target, EntityActor actor) {
        return authorized(target, actor, false).getVersion();
    }

    @Override public void requireReadable(EntityDataRef target, EntityActor actor) { authorized(target, actor, false); }

    @Override
    public void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion) {
        var observed = authorized(target, actor, true);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), observed.getProjectId(), ProjectScopeApi.ACTION_MANAGE));
        scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.userId(), observed.getProjectId(),
                ProjectScopeApi.ACTION_MANAGE, scope.treeVersion()));
        var row = mapper.selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(actor.tenantId(), observed.getProjectId(), observed.getId()));
        if (row == null || !Objects.equals(row.getVersion(), expectedVersion)) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        if (!Integer.valueOf(0).equals(row.getStatus())) throw exception(SITE_SURVEY_FORM_INVALID);
    }

    private SiteSurveyEntityDO authorized(EntityDataRef target, EntityActor actor, boolean write) {
        actor.requireTenant(target.entity());
        if (target.isRevision() || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType())) {
            throw exception(SITE_SURVEY_FORM_INVALID);
        }
        var row = mapper.selectById(target.entity().entityId());
        if (row == null || !actor.tenantId().equals(row.getTenantId())) throw exception(SITE_SURVEY_FORM_INVALID);
        if (!write && actor.isSystemObserver()) return row;
        if (write && actor.isSystemObserver()) throw exception(FORBIDDEN);
        boolean allowed = write ? permissions.hasAnyPermissions(actor.userId(), "pms:sol-site-survey:create", "pms:sol-site-survey:update")
                : permissions.hasAnyPermissions(actor.userId(), "pms:sol-site-survey:query");
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), row.getProjectId(),
                write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW));
        if (!allowed || scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(row.getProjectId())) throw exception(FORBIDDEN);
        return row;
    }
}
