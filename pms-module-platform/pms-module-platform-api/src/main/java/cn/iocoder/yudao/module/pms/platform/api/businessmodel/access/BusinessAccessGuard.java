package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;

/** 访问守卫：授权与数据范围的服务端实现点；空权限集合必须拒绝，不得扩大范围。 */
public interface BusinessAccessGuard {

    void requireReadable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode);

    void requireWritable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode);

    static void deny(String message) {
        throw new BusinessContractException("ACCESS_DENIED", message);
    }
}
