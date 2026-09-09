package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContactHistoryMapper extends BaseMapperX<ContactHistoryDO> {
    default cn.iocoder.yudao.framework.common.pojo.PageResult<ContactHistoryDO> selectProjectPage(
            cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.ProjectContactHistoryQuery query) {
        return selectPage(query.page(), new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ContactHistoryDO>()
                .eq(ContactHistoryDO::getTenantId, query.tenantId()).eq(ContactHistoryDO::getProjectId, query.projectId())
                .orderByDesc(ContactHistoryDO::getId));
    }
}
