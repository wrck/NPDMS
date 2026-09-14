package cn.iocoder.yudao.module.system.service.organization;
import cn.iocoder.yudao.module.system.service.dept.DeptServiceImpl;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import java.util.List;
/** Additive extension; CRUD, parent validation and local fields stay with the upstream implementation. */
@Service @Primary
public class ManagedDeptService extends DeptServiceImpl {
    @Resource private ManagedOrganizationGuard guard;
    @Override @Transactional(rollbackFor=Exception.class)
    @CacheEvict(cacheNames=RedisKeyConstants.DEPT_CHILDREN_ID_LIST,allEntries=true)
    public void updateDept(DeptSaveReqVO request) {
        if(request.getParentId()==null)request.setParentId(0L);
        guard.departmentUpdate(request);
        super.updateDept(request);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    @CacheEvict(cacheNames=RedisKeyConstants.DEPT_CHILDREN_ID_LIST,allEntries=true)
    public void deleteDept(Long id) { guard.departmentDelete(id);super.deleteDept(id); }
    @Override @Transactional(rollbackFor=Exception.class)
    @CacheEvict(cacheNames=RedisKeyConstants.DEPT_CHILDREN_ID_LIST,allEntries=true)
    public void deleteDeptList(List<Long> ids) {
        ids.stream().sorted().forEach(guard::departmentDelete);
        super.deleteDeptList(ids);
    }
}

