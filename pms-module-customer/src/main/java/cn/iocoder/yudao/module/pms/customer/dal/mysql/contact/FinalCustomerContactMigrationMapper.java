package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact;

import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FinalCustomerContactMigrationMapper {
    record ProjectQuery(Long tenantId, Long projectId) { }
    // 不将实体作为参数：框架自动填充不能修改原NULL操作者或原审计日期。
    record HistoricalInsert(ProjectCustomerContactDO values) { }
    List<ProjectCustomerContactDO> selectProjectForUpdate(@Param("query") ProjectQuery query);
    int insertHistorical(@Param("entry") HistoricalInsert entry);
}
