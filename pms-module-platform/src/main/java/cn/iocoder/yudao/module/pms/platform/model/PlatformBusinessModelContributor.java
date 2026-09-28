package cn.iocoder.yudao.module.pms.platform.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization.AuthorizationGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview.BusinessViewRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.AuthorizationGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.BusinessViewRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * B5 批次统一目录声明（P12）：平台公共业务记录进入统一目录（授权、业务视图修订、采集模板/任务）。
 * 模板发布/停用、任务调度回调、授权授予/撤销等专业写路径保留本域服务，声明不开放通用 create/save 操作；
 * 统一扩展/表单存储（P02 预分配主键契约）、审批引擎运行时、证据/结果流水、迁移控制台、命令/导出/凭据等
 * 技术设施与台账不进入目录（技术日志不伪装成业务模型）。
 */
@Component
public class PlatformBusinessModelContributor implements BusinessModelContributor {

    private final AuthorizationGrantMapper authorizationGrantMapper;
    private final BusinessViewRevisionMapper businessViewRevisionMapper;
    private final CollectionTemplateMapper collectionTemplateMapper;
    private final CollectionTaskMapper collectionTaskMapper;
    private final DeliveryRequirementMapper deliveryRequirementMapper;

    public PlatformBusinessModelContributor(AuthorizationGrantMapper authorizationGrantMapper,
                                            BusinessViewRevisionMapper businessViewRevisionMapper,
                                            CollectionTemplateMapper collectionTemplateMapper,
                                            CollectionTaskMapper collectionTaskMapper,
                                            DeliveryRequirementMapper deliveryRequirementMapper) {
        this.authorizationGrantMapper = authorizationGrantMapper;
        this.businessViewRevisionMapper = businessViewRevisionMapper;
        this.collectionTemplateMapper = collectionTemplateMapper;
        this.collectionTaskMapper = collectionTaskMapper;
        this.deliveryRequirementMapper = deliveryRequirementMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        List<BusinessModelDeclaration> declarations = new java.util.ArrayList<>();
        BusinessModelDescriptor grant = new BusinessModelDescriptor("PLT", "authorizationGrant",
                "PLT_AUTHORIZATION_GRANT", 1, BusinessModelKind.AGGREGATE_ROOT, "授权记录",
                "pms:project:authorization:query",
                List.of(required("subjectTypeCode", "主体类型", EntityField.Type.TEXT),
                        field("subjectId", "主体编号", EntityField.Type.NUMBER),
                        field("resourceContextCode", "资源上下文", EntityField.Type.TEXT),
                        field("resourceTypeCode", "资源类型", EntityField.Type.TEXT),
                        field("resourceId", "资源编号", EntityField.Type.NUMBER),
                        field("actionCode", "动作编码", EntityField.Type.TEXT),
                        field("scopeCode", "范围编码", EntityField.Type.TEXT),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("sourceContextCode", "来源上下文", EntityField.Type.TEXT),
                        field("sourceObjectType", "来源对象类型", EntityField.Type.TEXT),
                        field("sourceObjectId", "来源对象编号", EntityField.Type.TEXT),
                        field("grantedAt", "授予时间", EntityField.Type.DATETIME),
                        field("revokedAt", "撤销时间", EntityField.Type.DATETIME),
                        field("revokeReason", "撤销原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "plt_authorization_grant");
        declarations.add(new BusinessModelDeclaration(grant, AuthorizationGrantDO.class, authorizationGrantMapper, null));
        BusinessModelDescriptor viewRevision = new BusinessModelDescriptor("PLT", "businessViewRevision",
                "PLT_BUSINESS_VIEW_REVISION", 1, BusinessModelKind.AGGREGATE_ROOT, "业务视图修订",
                "pms:business-view:query",
                List.of(required("entityType", "实体类型", EntityField.Type.TEXT),
                        required("viewKey", "视图标识", EntityField.Type.TEXT),
                        field("revisionNo", "修订号", EntityField.Type.NUMBER),
                        field("ownerContext", "归属上下文", EntityField.Type.TEXT),
                        field("viewSource", "视图来源", EntityField.Type.TEXT),
                        field("componentKey", "组件标识", EntityField.Type.TEXT),
                        field("componentVersion", "组件版本", EntityField.Type.TEXT),
                        field("publishedAt", "发布时间", EntityField.Type.DATETIME),
                        field("disabledAt", "停用时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "plt_business_view_revision");
        declarations.add(new BusinessModelDeclaration(viewRevision, BusinessViewRevisionDO.class,
                businessViewRevisionMapper, null));
        BusinessModelDescriptor template = new BusinessModelDescriptor("PLT", "collectionTemplate",
                "PLT_COLLECTION_TEMPLATE", 1, BusinessModelKind.AGGREGATE_ROOT, "采集模板",
                "pms:collection-template:query",
                List.of(required("templateCode", "模板编码", EntityField.Type.TEXT),
                        required("name", "模板名称", EntityField.Type.TEXT),
                        field("ownerContext", "归属上下文", EntityField.Type.TEXT),
                        field("purpose", "用途", EntityField.Type.TEXT),
                        field("protocol", "协议", EntityField.Type.TEXT),
                        field("deviceModel", "设备型号", EntityField.Type.TEXT),
                        field("revision", "修订号", EntityField.Type.NUMBER),
                        field("commandText", "命令内容", EntityField.Type.TEXT),
                        field("contentHash", "内容摘要", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("publishedAt", "发布时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "plt_collection_template");
        declarations.add(new BusinessModelDeclaration(template, CollectionTemplateDO.class,
                collectionTemplateMapper, null));
        BusinessModelDescriptor task = new BusinessModelDescriptor("PLT", "collectionTask",
                "PLT_COLLECTION_TASK", 1, BusinessModelKind.AGGREGATE_ROOT, "采集任务",
                "pms:device-collection:query",
                List.of(required("platformTaskId", "平台任务标识", EntityField.Type.TEXT),
                        field("batchId", "批次编号", EntityField.Type.NUMBER),
                        field("sourceContext", "来源上下文", EntityField.Type.TEXT),
                        field("sourceObjectType", "来源对象类型", EntityField.Type.TEXT),
                        field("sourceObjectId", "来源对象编号", EntityField.Type.TEXT),
                        field("projectId", "项目标识", EntityField.Type.TEXT),
                        field("deviceId", "设备标识", EntityField.Type.TEXT),
                        field("deviceName", "设备名称", EntityField.Type.TEXT),
                        field("host", "主机地址", EntityField.Type.TEXT),
                        field("port", "端口", EntityField.Type.NUMBER),
                        field("protocol", "协议", EntityField.Type.TEXT),
                        field("templateId", "模板标识", EntityField.Type.TEXT),
                        field("templateVersion", "模板版本", EntityField.Type.TEXT),
                        field("credentialMode", "凭据模式", EntityField.Type.TEXT),
                        field("completionMode", "完成模式", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("technicalStage", "技术阶段", EntityField.Type.TEXT),
                        field("externalTaskId", "外部任务标识", EntityField.Type.TEXT),
                        field("externalStatus", "外部状态", EntityField.Type.TEXT),
                        field("failureCategory", "失败分类", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "plt_collection_task");
        declarations.add(new BusinessModelDeclaration(task, CollectionTaskDO.class, collectionTaskMapper, null));
        // P06R：统一交付要求根（TEMPLATE_FROZEN 承接 acc_project_deliverable 身份），只读目录呈现，
        // 写路径经 PlatformDeliveryRequirementApi / 提交台账，不开放通用 create/save。
        BusinessModelDescriptor deliveryRequirement = new BusinessModelDescriptor("PLT", "deliveryRequirement",
                "PLT_DELIVERY_REQUIREMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "统一交付要求",
                "pms:project:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("typeCode", "交付件编码", EntityField.Type.TEXT),
                        required("name", "交付件名称", EntityField.Type.TEXT),
                        field("stageCode", "阶段", EntityField.Type.TEXT),
                        field("taskCode", "任务", EntityField.Type.TEXT),
                        field("required", "是否必备", EntityField.Type.BOOLEAN),
                        field("minimumQuantity", "最少有效材料数", EntityField.Type.NUMBER),
                        field("planVersionId", "计划版本", EntityField.Type.NUMBER),
                        field("sourceDefinitionId", "来源定义", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("requirementKind", "要求种类", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "plt_delivery_requirement");
        declarations.add(new BusinessModelDeclaration(deliveryRequirement, DeliveryRequirementDO.class,
                deliveryRequirementMapper, null));
        return declarations;
    }
}
