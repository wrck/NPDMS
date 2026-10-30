package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleCandidateFactMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleCandidateFactQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ActorRoleMembershipQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleCandidateFactApiImpl implements RoleCandidateFactApi {
    private final RoleCandidateFactMapper mapper;
    private final RoleMapper roleMapper;
    public List<Candidate> inspect(Query query) { return resolve(query, false); }
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Candidate> lockAndInspect(Query query) { return resolve(query, true); }
    public RoleReference inspectRole(Query query) {
        validateRoleQuery(query);
        var role = roleMapper.selectByCode(query.roleCode());
        return role == null || !Objects.equals(role.getTenantId(), query.tenantId()) || !Integer.valueOf(0).equals(role.getStatus())
                ? null : new RoleReference(role.getId(), role.getCode());
    }
    public List<ActorMembership> inspectActor(ActorQuery query) { return actorMemberships(query, false); }
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<ActorMembership> lockAndInspectActor(ActorQuery query) { return actorMemberships(query, true); }
    private List<ActorMembership> actorMemberships(ActorQuery query, boolean locking) {
        if (query == null || query.actorUserId() == null || query.actorUserId() <= 0)
            throw new IllegalArgumentException("角色成员查询缺少操作者");
        requireTenant(query.tenantId());
        return mapper.selectActorMemberships(new ActorRoleMembershipQuery(query.tenantId(), query.actorUserId(), locking));
    }
    private List<Candidate> resolve(Query query, boolean locking) {
        validateRoleQuery(query);
        return mapper.selectCandidates(new RoleCandidateFactQuery(query.tenantId(), query.roleCode(), locking));
    }
    private static void validateRoleQuery(Query query) {
        if (query == null || query.roleCode() == null || query.roleCode().isBlank()) {
            throw new IllegalArgumentException("角色候选查询缺少受信租户或角色编码");
        }
        requireTenant(query.tenantId());
    }
    private static void requireTenant(Long tenantId) {
        if (tenantId == null || !Objects.equals(tenantId, TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
            throw new IllegalArgumentException("角色查询缺少受信租户");
    }
}
