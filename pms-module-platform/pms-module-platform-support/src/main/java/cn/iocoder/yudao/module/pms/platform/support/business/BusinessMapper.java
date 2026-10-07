package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** A concrete business Mapper only binds E; complex business queries remain in its own XML. */
public interface BusinessMapper<E extends BaseProjectBusinessEntity> extends BaseMapper<E> {
    default PageResult<E> selectBusinessPage(BusinessReadQuery query) {
        return BusinessMapperQueries.page(this, query);
    }
}
