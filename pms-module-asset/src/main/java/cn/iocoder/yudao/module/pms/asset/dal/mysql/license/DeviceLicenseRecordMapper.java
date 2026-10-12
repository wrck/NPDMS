package cn.iocoder.yudao.module.pms.asset.dal.mysql.license;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.license.DeviceLicenseRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceLicenseRecordMapper extends BaseMapperX<DeviceLicenseRecordDO> {
    record RecordQuery(Long tenantId, Long id) {}
    record PageQuery(Long tenantId, List<Long> ids, int offset, int limit) {}
    DeviceLicenseRecordDO selectRecordForUpdate(@Param("query") RecordQuery query);
    List<DeviceLicenseRecordDO> selectVisiblePage(@Param("query") PageQuery query);
    long countVisible(@Param("query") PageQuery query);
    default PageResult<DeviceLicenseRecordDO> page(PageQuery query) {
        if(query.ids()==null || query.ids().isEmpty()) return PageResult.empty();
        return new PageResult<>(selectVisiblePage(query),countVisible(query));
    }
}
