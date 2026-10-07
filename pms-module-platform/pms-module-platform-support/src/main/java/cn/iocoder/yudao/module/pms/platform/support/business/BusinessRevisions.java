package cn.iocoder.yudao.module.pms.platform.support.business;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldValue;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import java.util.List;
import java.util.Map;

/** Inherited revision HTTP contract; ordinary businesses can choose the versioned default superclass. */
public interface BusinessRevisions {
    List<EntityVersionProvider.Revision> revisions(Long id,Long beforeId,int limit);
    Map<String,EntityFieldValue> revisionValues(Long id,Long revisionId);
    BusinessFormData revisionForm(Long id,Long revisionId);
    List<cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionApi.FieldDifference> compareRevisions(Long id,Long left,Long right);
    BusinessOperationReceipt createRevision(Long id,Long version,Long sourceRevisionId,String reason,String key);
    BusinessOperationReceipt saveRevision(Long id,Long version,Long revisionId,Long revisionVersion,Map<String,Object> values,String key);
    BusinessOperationReceipt completeRevision(Long id,Long version,Long revisionId,Long revisionVersion,String key);
    BusinessOperationReceipt discardRevision(Long id,Long version,Long revisionId,Long revisionVersion,String key);
}
