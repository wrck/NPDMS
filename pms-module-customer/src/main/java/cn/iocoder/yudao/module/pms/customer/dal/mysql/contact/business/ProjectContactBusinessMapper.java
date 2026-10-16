package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.business;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.business.ProjectContactBusinessDO;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessMapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ProjectContactBusinessMapper extends BusinessMapper<ProjectContactBusinessDO> {
    record DeletionAudit(Long tenantId,Long id,Long version,Long actorId) { }
    int markDeletion(DeletionAudit command);
}
