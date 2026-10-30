package cn.iocoder.yudao.module.pms.platform.support.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import java.math.BigDecimal;
import java.util.*;

/** Declaration-driven scope enforcement; never guesses a projectId field or expands missing scope. */
public final class DeclaredBusinessScopeSupport {
    private final List<BusinessScopeAccess> policies;
    public DeclaredBusinessScopeSupport(List<BusinessScopeAccess> policies) { this.policies=List.copyOf(policies); }
    public List<BusinessFieldFilter> queryFilters(BusinessModelDescriptor model,EntityActor actor) {
        var binding=binding(model);
        if (tenant(binding)) return List.of();
        var ids=policy(binding).readableScopeIds(actor);
        return List.of(new BusinessFieldFilter(binding.ownershipFieldCode(),BusinessFieldFilter.Operator.IN,
                ids==null?List.of():new ArrayList<Object>(ids)));
    }
    public void requireReadable(BusinessModelDescriptor model,Map<String,Object> values,EntityActor actor) {
        var binding=binding(model);
        if (!tenant(binding)) policy(binding).requireReadable(scopeId(binding,values),actor);
    }
    public void requireWritable(BusinessModelDescriptor model,Map<String,Object> values,EntityActor actor,boolean lock) {
        var binding=binding(model);
        if (!tenant(binding)) policy(binding).requireWritable(scopeId(binding,values),actor,lock);
    }
    public void requireWritableAfterChange(BusinessModelDescriptor model, Map<String, Object> current,
                                            Map<String, Object> proposed, EntityActor actor) {
        var binding = binding(model);
        if (!tenant(binding)) {
            Set<Long> ids = Set.copyOf(List.of(scopeId(binding, current), scopeId(binding, proposed)));
            policy(binding).requireWritableScopes(ids, actor, true);
        }
    }
    private BusinessScopeBinding binding(BusinessModelDescriptor model) {
        var binding=model.scopeBinding();
        if(binding==null || binding.policyRef()==null || binding.policyRef().isBlank())
            throw new BusinessContractException("SCOPE_POLICY_NOT_DECLARED","默认访问缺少范围声明: "+model.stableCode());
        if(!tenant(binding) && (binding.ownershipFieldCode()==null || binding.ownershipFieldCode().isBlank()))
            throw new BusinessContractException("SCOPE_MAPPING_INVALID","范围策略缺少归属字段: "+model.stableCode());
        return binding;
    }
    private boolean tenant(BusinessScopeBinding binding) {
        if(!"tenant".equals(binding.policyRef())) return false;
        if(binding.ownershipFieldCode()!=null) throw new BusinessContractException("SCOPE_MAPPING_INVALID","租户范围不得携带对象归属字段");
        return true;
    }
    private BusinessScopeAccess policy(BusinessScopeBinding binding) {
        var matches=policies.stream().filter(p->binding.policyRef().equals(p.policyRef())).toList();
        if(matches.size()!=1) throw new BusinessContractException("SCOPE_POLICY_UNAVAILABLE","范围策略必须有唯一实现: "+binding.policyRef());
        return matches.getFirst();
    }
    private Long scopeId(BusinessScopeBinding binding,Map<String,Object> values) {
        try {
            var value=values.get(binding.ownershipFieldCode());
            long id=new BigDecimal(Objects.requireNonNull(value).toString()).longValueExact();
            if(id<=0) throw new IllegalArgumentException();
            return id;
        } catch(RuntimeException invalid) {
            throw new BusinessContractException("SCOPE_ID_INVALID","归属对象标识无效: "+binding.ownershipFieldCode());
        }
    }
}
