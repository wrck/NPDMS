package cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;

/** 集合访问端口：返回成员、下一游标、完整性和不可用原因。 */
public interface BusinessCollectionPort {

    BusinessEntitySlice members(BusinessCollectionQuery query, EntityActor actor);
}
