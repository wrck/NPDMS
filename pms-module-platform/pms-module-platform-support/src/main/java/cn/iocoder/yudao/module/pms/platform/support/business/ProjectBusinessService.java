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
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Definition runtimeDefinition();
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Observation runtimeObservation(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query query,boolean lock);
    java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Reference> runtimeCandidates(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates query);
    java.util.Set<String> runtimeActions(cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Observation runtimeForUser(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context,Long id,boolean lock,String expectedVersion);
    BusinessModelViews.ModelDetailVO configurationModel(boolean write);
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration fieldConfiguration();
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(long version, java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field> fields);
    BusinessModelViews.ModelDetailVO model();
    E input(Map<String,Object> values);
    Map<String,Object> readableValues(E entity);
    E get(Long id);
    PageResult<E> page(BusinessPageQuery query);
    BusinessOperationReceipt create(E entity, String idempotencyKey);
    BusinessOperationReceipt update(Long id, E values, Set<String> changedFields, Long version, String idempotencyKey);
    BusinessOperationReceipt delete(Long id, Long version, String idempotencyKey);
    BusinessOperationReceipt receipt(String operation, String idempotencyKey);
    BusinessFormData form(Long id);
    BusinessOperationReceipt saveForm(Long id, Map<String,Object> values, Long version, String key);
    Long requireDeliveryAccess(Long id, boolean write, boolean lock);
}
