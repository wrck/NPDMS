package cn.iocoder.yudao.module.system.api.organization;

import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import java.util.List;
import java.util.Set;

/** Reuses the existing organization entities; only provenance and relationship keys are separate. */
public interface ManagedOrganizationApi {
    record Node(CompanyDO company, DeptDO department, String managedBy) {
        public String type(){return company!=null?"COMPANY":"DEPARTMENT";}
        public Long id(){return company!=null?company.getId():department.getId();}
        public String code(){return company!=null?company.getCode():department.getCode();}
        public String name(){return company!=null?company.getName():department.getName();}
        public Integer status(){return company!=null?company.getStatus():department.getStatus();}
        public Integer version(){return company!=null?company.getVersion():department.getVersion();}
        public Long parentId(){return company!=null?0L:department.getParentId();}
        public Integer sort(){return company!=null?0:department.getSort();}
    }
    record Entry(String key, String parentKey, Node entity) {
        public String type(){return entity.type();}
        public Long targetId(){return entity.id();}
        public String code(){return entity.code();}
        public String name(){return entity.name();}
        public Integer status(){return entity.status();}
        public Integer sort(){return entity.sort();}
    }
    /** assignedIdKeys identifies new entries whose entity ID is explicitly assigned by the caller. */
    record Command(String owner, List<Entry> entries, boolean adoptExisting, Set<String> assignedIdKeys,
                   String loadingMode, Set<String> loadedKeys) {}
    record Result(String key, Node before, Node after, String action) {}
    List<Node> list();
    List<Result> preview(Command command);
    List<Result> apply(Command command);
    List<Result> previewReplacement(Command command);
    List<Result> replaceAll(Command command);
    void refreshCaches();
}
