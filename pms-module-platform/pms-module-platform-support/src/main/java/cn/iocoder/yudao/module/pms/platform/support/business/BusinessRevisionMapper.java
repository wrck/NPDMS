package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.entity.MutableEntityRevision;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/** Own revision table; fixed metadata names come from the revision entity contract, never HTTP input. */
public interface BusinessRevisionMapper<R extends BaseProjectBusinessEntity & MutableEntityRevision> extends BaseMapper<R> {
    record History(Long tenantId,Long entityId,Long beforeRevisionId,int limit) { }
    default List<R> selectHistory(History query){
        if(query.limit()<1 || query.limit()>200)throw new BusinessContractException("QUERY_INVALID","Invalid revision history limit");
        return selectPage(new Page<R>(1,query.limit(),false),new QueryWrapper<R>().eq("tenant_id",query.tenantId()).eq("entity_id",query.entityId())
                .lt(query.beforeRevisionId()!=null,"id",query.beforeRevisionId()).orderByDesc("id")).getRecords();
    }
}
