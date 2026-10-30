package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;

/** 标准受控访问：可信操作者与读取场景由调用方给出，租户、权限和数据范围由服务端确定。 */
public interface BusinessEntityAccessPort {

    BusinessEntityData read(EntityDataRef ref, EntityActor actor, String sceneCode);

    BusinessEntitySlice query(BusinessEntityPageQuery query, EntityActor actor);
}
