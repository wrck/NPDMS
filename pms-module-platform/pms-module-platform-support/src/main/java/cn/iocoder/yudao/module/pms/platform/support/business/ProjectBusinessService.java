package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import java.util.Map;
import java.util.Set;

/** Inherited business API. No caller-supplied owner, model name, Mapper or operation dispatcher. */
public interface ProjectBusinessService<E extends BaseProjectBusinessEntity> extends BusinessDeliverables {
    BusinessModelDescriptor definition();
    /** Query-authorized metadata without computing permissions for unrelated write operations. */
    BusinessModelDescriptor readableDefinition();
    /** Internal identity guard for common evidence reads; never mapped as an HTTP endpoint. */
    cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef requireEvidenceScope(cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef entity,Long projectId,boolean lock);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionResult evaluate(Long id,
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessCondition condition);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionResult completion(Long id);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Definition runtimeDefinition();
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Observation runtimeObservation(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query query,boolean lock);
    java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Reference> runtimeCandidates(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates query);
    java.util.Set<String> runtimeActions(cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserInspection runtimeInspectForUser(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context,java.util.List<Long> ids);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Observation runtimeForUser(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context,Long id,boolean lock,String expectedVersion);
    BusinessModelViews.ModelDetailVO configurationModel(boolean write);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration fieldConfiguration();
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(long version, java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field> fields);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration configuration);
    BusinessModelViews.ModelDetailVO model();
    BusinessModelViews.ModelDetailVO model(String profile);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Read initializePage(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Request request,
            java.util.List<Long> observeEntityIds);
    E input(Map<String,Object> values);
    Map<String,Object> readableValues(E entity);
    E get(Long id);
    PageResult<E> page(BusinessPageQuery query);
    BusinessOperationReceipt create(E entity, String idempotencyKey);
    BusinessOperationReceipt createForm(Map<String,Object> values,String idempotencyKey);
    BusinessOperationReceipt updateForm(Long id,Map<String,Object> values,Long version,String idempotencyKey);
    BusinessFormData formDefaults(Long projectId);
    java.util.List<BusinessFormOption> formOptions(Long projectId);
    BusinessOperationReceipt update(Long id, E values, Set<String> changedFields, Long version, String idempotencyKey);
    BusinessOperationReceipt delete(Long id, Long version, String idempotencyKey);
    BusinessOperationReceipt receipt(String operation, String idempotencyKey);
    BusinessFormData form(Long id);
    BusinessOperationReceipt saveForm(Long id, Map<String,Object> values, Long version, String key);
    Long requireDeliveryAccess(Long id, boolean write, boolean lock);
}
