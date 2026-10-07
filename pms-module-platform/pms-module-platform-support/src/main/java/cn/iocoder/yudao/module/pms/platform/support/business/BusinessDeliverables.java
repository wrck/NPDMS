package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import java.util.Objects;

/** Every business inherits the same delivery operations and the same underlying material entity. */
public interface BusinessDeliverables {
    DefaultBusinessDeliveryApi.Scope deliveryScope(Long entityId, String deliverableType);
    DefaultBusinessDeliveryApi deliveryApi();
    default java.util.List<Record> copyDeliveries(Long sourceId,Long targetId,String requestKey) {
        return deliveryApi().copy(new DefaultBusinessDeliveryApi.Copy(deliveryScope(sourceId,null),deliveryScope(targetId,null),requestKey));
    }
    default Record uploadDelivery(Long entityId, String type, DefaultBusinessDeliveryApi.UploadFile file, String key) {
        return uploadDelivery(entityId,deliveryScope(entityId,type),file,key);
    }
    default Record uploadDelivery(Long entityId, DefaultBusinessDeliveryApi.Scope claimed, DefaultBusinessDeliveryApi.UploadFile file, String key) {
        var expected=deliveryScope(entityId,claimed.deliverableType());
        if(!expected.equals(claimed)) throw new BusinessContractException("DELIVERY_OWNER_MISMATCH","Upload scope changed");
        return deliveryApi().upload(claimed,file,key);
    }
    default DefaultBusinessDeliveryApi.Completion deliveryCompletion(Long entityId, DefaultBusinessDeliveryApi.Scope claimed) {
        if(!deliveryScope(entityId,claimed.deliverableType()).equals(claimed))
            throw new BusinessContractException("DELIVERY_OWNER_MISMATCH","Completion scope changed");
        return deliveryApi().completion(claimed);
    }
    default PageResult<Record> deliveries(Long entityId, String type, int page, int size) {
        var scope=deliveryScope(entityId,type);
        return deliveryApi().list(scope.projectId(),type,scope.businessType(),scope.businessEntityKey(),page,size);
    }
    default DefaultBusinessDeliveryApi.Completion deliveryCompletion(Long entityId, String type) {
        return deliveryApi().completion(deliveryScope(entityId,type));
    }
    default Record delivery(Long entityId, Long materialId) {
        var scope=deliveryScope(entityId,null);var row=deliveryApi().get(materialId);
        if (!Objects.equals(scope.projectId(),row.projectId()) || !scope.businessType().equals(row.businessType())
                || !scope.businessEntityKey().equals(row.businessEntityKey()))
            throw new BusinessContractException("DELIVERY_OWNER_MISMATCH","Material belongs to another business");
        return row;
    }
    default FileEvidenceApi.Document deliveryFile(Long entityId, Long materialId) {
        delivery(entityId,materialId);return deliveryApi().file(materialId);
    }
    default Record editDelivery(Long entityId, Long materialId, Long version, String title) {
        delivery(entityId,materialId);return deliveryApi().edit(materialId,version,title);
    }
    default void deleteDelivery(Long entityId, Long materialId, Long version) {
        delivery(entityId,materialId);deliveryApi().delete(materialId,version);
    }
}
