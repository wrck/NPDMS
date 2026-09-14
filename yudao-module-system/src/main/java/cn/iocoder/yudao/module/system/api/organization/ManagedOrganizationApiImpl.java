package cn.iocoder.yudao.module.system.api.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagedOrganizationApiImpl implements ManagedOrganizationApi {
    private final CompanyMapper companies;
    private final DeptMapper departments;
    private final ManagedOrganizationMapper locks;
    private final CacheManager cacheManager;
    private final cn.iocoder.yudao.module.system.dal.mysql.organization.OrganizationOwnershipMapper ownerships;
    private final List<OrganizationClearGuard> clearGuards;

    public List<Node> list() {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var owners = ownerMap();
        List<Node> nodes = new ArrayList<>();
        companies.selectList(new LambdaQueryWrapperX<CompanyDO>().eq(CompanyDO::getTenantId, tenant))
                .forEach(c -> nodes.add(node(c,owners)));
        departments.selectList(new LambdaQueryWrapperX<DeptDO>().eq(DeptDO::getTenantId, tenant))
                .forEach(d -> nodes.add(node(d,owners)));
        return nodes;
    }
    public List<Result> preview(Command command) { return plan(command, list()); }
    @Transactional(rollbackFor=Exception.class)
    public List<Result> previewReplacement(Command command) {
        var query=new ManagedOrganizationMapper.TenantQuery(TenantContextHolder.getRequiredTenantId());
        var owners=ownerMap();List<Node> current=new ArrayList<>();
        locks.selectAllCompaniesForUpdate(query).forEach(c->current.add(node(c,owners)));
        locks.selectAllDepartmentsForUpdate(query).forEach(d->current.add(node(d,owners)));
        checkClear(current);
        var results=cleared(current);results.addAll(plan(command,List.of()));return results;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Result> replaceAll(Command command) { return applyInternal(command,true); }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Result> apply(Command command) {
        return applyInternal(command,false);
    }
    private List<Result> applyInternal(Command command,boolean replace) {
        var query = new ManagedOrganizationMapper.TenantQuery(TenantContextHolder.getRequiredTenantId());
        List<Node> current = new ArrayList<>();
        var lockedCompanies=replace?locks.selectAllCompaniesForUpdate(query):locks.selectCompaniesForUpdate(query);
        var lockedDepartments=replace?locks.selectAllDepartmentsForUpdate(query):locks.selectDepartmentsForUpdate(query);
        var owners=ownerMap();
        lockedCompanies.forEach(c -> current.add(node(c,owners)));
        lockedDepartments.forEach(d -> current.add(node(d,owners)));
        if(replace)checkClear(current);
        List<Result> planned = plan(command, replace?List.of():current);
        if (planned.stream().anyMatch(r -> "CONFLICT".equals(r.action())))
            throw new IllegalArgumentException("组织主键、编码、字段归属或接管前置值冲突");
        if(replace) {
            locks.deleteOwnerships(query);locks.deleteDepartments(query);locks.deleteCompanies(query);
        }
        Map<String, Long> ids = new HashMap<>();
        for (Result r : planned) {
            if (r.before() != null) ids.put(r.key(), r.before().id());
            else if ("COMPANY".equals(r.after().type())) {
                var c = new CompanyDO(); c.setId(r.after().id()).setCode(r.after().code()).setName(r.after().name())
                        .setStatus(r.after().status()).setVersion(0);
                c.setTenantId(query.tenantId());
                c.setCreator("organization_sync"); c.setUpdater("organization_sync");
                companies.insert(c); ids.put(r.key(), c.getId());
            } else {
                var d = new DeptDO(); d.setId(r.after().id()).setCode(r.after().code()).setName(r.after().name()).setParentId(0L)
                        .setSort(r.after().sort()).setStatus(r.after().status()).setVersion(0);
                d.setTenantId(query.tenantId());
                d.setCreator("organization_sync"); d.setUpdater("organization_sync");
                departments.insert(d); ids.put(r.key(), d.getId());
            }
        }
        Map<String, Entry> entries = command.entries().stream().collect(Collectors.toMap(Entry::key, Function.identity()));
        List<Result> results = replace?cleared(current):new ArrayList<>();
        for (Result r : planned) {
            Entry e = entries.get(r.key());
            if ("SKIPPED".equals(r.action())) { results.add(r); continue; }
            Long parentId = e.parentKey() == null ? 0L : ids.get(e.parentKey());
            long id = ids.get(r.key());
            Node after = desired(e, id, parentId, r.before() == null ? 0 : r.before().version(), command.owner());
            if (!Set.of("UNCHANGED", "ADOPTED").contains(r.action())) {
                int version = r.before() == null ? 0 : r.before().version() + 1;
                if ("COMPANY".equals(e.type())) {
                    var c = new CompanyDO(); c.setId(id).setCode(e.code()).setName(e.name())
                            .setStatus(e.status()).setVersion(version); c.setUpdater("organization_sync"); companies.updateById(c);
                } else {
                    var d = new DeptDO(); d.setId(id).setCode(e.code()).setName(e.name())
                            .setParentId(parentId).setSort(e.sort()).setStatus(e.status()).setVersion(version);
                    d.setUpdater("organization_sync"); departments.updateById(d);
                }
            }
            if(r.before()==null || r.before().managedBy()==null) {
                var ownership=new cn.iocoder.yudao.module.system.dal.dataobject.organization.OrganizationOwnershipDO()
                        .setObjectType(e.type()).setTargetId(id).setManagedBy(command.owner());
                ownership.setCreator("organization_sync");ownership.setUpdater("organization_sync");
                ownership.setTenantId(query.tenantId());ownerships.insert(ownership);
            }
            results.add(new Result(r.key(), r.before(), after, r.action()));
        }
        return results;
    }

    private void checkClear(List<Node> current) {
        var query=new ManagedOrganizationMapper.TenantQuery(TenantContextHolder.getRequiredTenantId());
        long references=locks.countClearReferences(query);
        if(references>0)throw new IllegalArgumentException("不能清空公司/部门：仍有 "+references+" 条用户所属部门、组织授权或角色部门范围引用，请先在系统管理中处理引用");
        Set<Long> companyIds=new HashSet<>(),departmentIds=new HashSet<>();
        current.forEach(n->{if("COMPANY".equals(n.type()))companyIds.add(n.id());else departmentIds.add(n.id());});
        var scope=new OrganizationClearGuard.Scope(query.tenantId(),companyIds,departmentIds);
        clearGuards.forEach(g->g.check(scope));
    }
    private static List<Result> cleared(List<Node> current) {
        List<Result> results=new ArrayList<>();
        current.forEach(n->results.add(new Result("CLEARED:"+n.type()+":"+n.id(),n,n,"CLEARED")));
        return results;
    }

    static List<Result> plan(Command command, List<Node> current) {
        if (!Set.of("UPSERT","INSERT_ONLY","INSERT_IGNORE").contains(command.loadingMode()))
            throw new IllegalArgumentException("组织加载策略无效");
        if (command.owner() == null || command.owner().isBlank() || command.owner().length() > 128)
            throw new IllegalArgumentException("组织来源标识无效");
        Map<String, Entry> entries = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        Set<String> targetIds = new HashSet<>();
        Map<String, Node> byId = current.stream().collect(Collectors.toMap(n -> n.type()+":"+n.id(), Function.identity()));
        Map<String, Node> byCode = current.stream().filter(n -> n.code()!=null)
                .collect(Collectors.toMap(n -> n.type()+":"+canonical(n.code()), Function.identity()));
        for (Entry e : command.entries()) {
            if (!Set.of("COMPANY","DEPARTMENT").contains(e.type()) || e.key()==null
                    || e.code()==null || e.code().isBlank() || e.code().length()>64
                    || e.name()==null || e.name().isBlank() || e.name().length()>("COMPANY".equals(e.type())?128:30)
                    || e.status()==null || (e.status()!=0 && e.status()!=1) || e.sort()==null)
                throw new IllegalArgumentException("组织字段无效");
            if (entries.put(e.key(),e)!=null || !codes.add(e.type()+":"+canonical(e.code())))
                throw new IllegalArgumentException("组织来源键或编码重复");
            if (e.targetId()!=null && (e.targetId()<=0 || !targetIds.add(e.type()+":"+e.targetId())))
                throw new IllegalArgumentException("组织目标主键无效或重复");
            if (command.assignedIdKeys().contains(e.key()) && e.targetId()==null)
                throw new IllegalArgumentException("指定主键创建必须提供目标主键");
        }
        if (!entries.keySet().containsAll(command.assignedIdKeys()))
            throw new IllegalArgumentException("指定主键创建引用了不存在的组织");
        for (Entry e : entries.values()) {
            Set<String> path = new HashSet<>();
            Entry n=e;
            while (n.parentKey()!=null) {
                if (!path.add(n.key())) throw new IllegalArgumentException("部门父级存在环");
                n=entries.get(n.parentKey());
                if (n==null || !"DEPARTMENT".equals(n.type())) throw new IllegalArgumentException("部门父级不存在");
            }
        }
        List<Result> result = new ArrayList<>();
        for (Entry e : entries.values()) {
            Node existing=e.targetId()==null?byCode.get(e.type()+":"+canonical(e.code())):byId.get(e.type()+":"+e.targetId());
            Node codeOwner=byCode.get(e.type()+":"+canonical(e.code()));
            Entry parent=e.parentKey()==null?null:entries.get(e.parentKey());
            Node parentNode=parent==null?null:(parent.targetId()==null
                    ?byCode.get("DEPARTMENT:"+canonical(parent.code())):byId.get("DEPARTMENT:"+parent.targetId()));
            boolean assigned=command.assignedIdKeys().contains(e.key());
            Long parentId=parent==null?0L:parentNode!=null?parentNode.id():
                    command.assignedIdKeys().contains(parent.key())?parent.targetId():null;
            Node desired=desired(e,existing!=null?existing.id():assigned?e.targetId():null,parentId,existing==null?0:existing.version(),command.owner());
            boolean same=existing!=null && Objects.equals(existing.code(),e.code()) && Objects.equals(existing.name(),e.name())
                    && Objects.equals(existing.status(),e.status()) && ("COMPANY".equals(e.type())
                    || (Objects.equals(existing.parentId(),parentId)&&Objects.equals(existing.sort(),e.sort())));
            boolean owned=existing!=null && Objects.equals(existing.managedBy(),command.owner());
            boolean conflict=(e.targetId()!=null && existing==null && !assigned)
                    || (codeOwner!=null && (existing==null || !codeOwner.id().equals(existing.id())))
                    || (existing!=null && !owned && !(existing.managedBy()==null && command.adoptExisting() && same));
            String action=conflict?"CONFLICT":existing==null?"CREATED":!owned?"ADOPTED":same?"UNCHANGED":
                    existing.status()==0 && e.status()==1?"DISABLED":"UPDATED";
            if (!conflict && existing!=null && command.loadedKeys().contains(e.key())) {
                if ("INSERT_ONLY".equals(command.loadingMode())) action="CONFLICT";
                else if ("INSERT_IGNORE".equals(command.loadingMode()) && owned) { action="SKIPPED"; desired=existing; }
            }
            result.add(new Result(e.key(),existing,desired,action));
        }
        return result;
    }
    private static String canonical(String s) { return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).stripTrailing(); }
    private static Node node(CompanyDO company,Map<String,String> owners) {
        return new Node(company,null,owners.get("COMPANY:"+company.getId()));
    }
    private static Node node(DeptDO department,Map<String,String> owners) {
        return new Node(null,department,owners.get("DEPARTMENT:"+department.getId()));
    }
    private static Node desired(Entry entry,Long id,Long parentId,Integer version,String owner) {
        if(entry.entity().company()!=null) {
            var company=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(entry.entity().company(),CompanyDO.class);
            company.setId(id).setVersion(version);
            return new Node(company,null,owner);
        }
        var department=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(entry.entity().department(),DeptDO.class);
        department.setId(id).setParentId(parentId).setVersion(version);
        return new Node(null,department,owner);
    }
    private Map<String,String> ownerMap() {
        Map<String,String> owners=new HashMap<>();
        ownerships.selectTenant(new ManagedOrganizationMapper.TenantQuery(TenantContextHolder.getRequiredTenantId()))
                .forEach(o->owners.put(o.getObjectType()+":"+o.getTargetId(),o.getManagedBy()));
        return owners;
    }
    public void refreshCaches() {
        var cache=cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if(cache!=null) cache.clear();
    }
}
