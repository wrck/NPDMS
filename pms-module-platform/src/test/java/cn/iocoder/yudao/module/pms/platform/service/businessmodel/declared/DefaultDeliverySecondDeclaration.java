package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import java.util.List;
/** Second ordinary business: declaration + persisted fields + mapper only. */
public record DefaultDeliverySecondDeclaration(DefaultDeliverySecondMapper mapper) implements BusinessModelContributor {
    @Override public List<BusinessModelDeclaration> declarations() {
        return List.of(new BusinessModelDeclaration(new BusinessModelDescriptor("IT","secondDelivery","IT_SECOND_DELIVERY",1,
                BusinessModelKind.AGGREGATE_ROOT,"Second ordinary business","it:second:query",
                List.of(new BusinessFieldDescriptor("projectRef","Project",EntityField.Type.NUMBER,true,true,true,null),
                        new BusinessFieldDescriptor("title","Title",EntityField.Type.TEXT,true,true,true,null)),List.of(),
                List.of(new BusinessOperationDescriptor("create",1,"Create",BusinessOperationDescriptor.StandardOperationKind.CREATE,"it:second:create"),
                        new BusinessOperationDescriptor("save",1,"Save",BusinessOperationDescriptor.StandardOperationKind.UPDATE,"it:second:update")),
                List.of(),null,new BusinessScopeBinding("project","projectRef")),DefaultDeliverySecondDO.class,mapper,null));
    }
}
