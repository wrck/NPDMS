package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Registers source aliases against the formal SYSTEM catalogue; never creates a company. */
@Component @RequiredArgsConstructor
public class DppmsCompanyReferenceSyncAdapter implements DataSyncAdapter {
    private final ManagedOrganizationApi organizations;
    private final SyncTaskService tasks;
    @Override public Descriptor descriptor(){return new Descriptor("DPPMS_COMPANY_REFERENCES","DPPMS公司来源别名",
            List.of(new ObjectDescriptor("COMPANY_REFERENCE","t_company原身份",List.of(
                    new Field("sourceCompanyId","原公司ID","STRING",true),new Field("sourceCompanyCode","原公司码","STRING",true)),
                    "SYSTEM","Company","system_company",false)),List.of("RETAIN"),List.of("UPSERT"),false);}
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public List<Change> preview(Batch batch){return execute(new DppmsConsolidation.OrganizationPreviewBatch(batch,List.of()));}
    List<Change> previewAfterOrganization(DppmsConsolidation.OrganizationPreviewBatch preview){return execute(preview);}
    @Override public List<Change> apply(Batch batch){return execute(new DppmsConsolidation.OrganizationPreviewBatch(batch,List.of()));}
    @Override public void refreshCaches(){}
    private List<Change> execute(DppmsConsolidation.OrganizationPreviewBatch input){
        var batch=input.batch();if(batch.rows().isEmpty())return List.of();
        long tenant=TenantContextHolder.getRequiredTenantId();String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))throw new IllegalArgumentException("公司别名任务所有权无效");
        var task=tasks.required(Long.parseLong(owner[2]));
        if(!"DPPMS".equals(task.getSourceSystem()) || !DppmsConsolidation.accepts(task,"DPPMS_COMPANY_REFERENCES")
                || !batch.full() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            throw new IllegalArgumentException("公司来源别名仅允许保留正式目录的完整核对");
        Map<String,ManagedOrganizationApi.Node> catalogue=new HashMap<>();
        for(var node:organizations.list())if(node.company()!=null && catalogue.putIfAbsent(node.code(),node)!=null)
            throw new IllegalArgumentException("正式公司编码重复，不能任选原公司别名");
        for(var result:input.preceding())if(result.after()!=null && result.after().company()!=null){
            var node=result.after();var previous=catalogue.get(node.code());
            if(previous!=null && !Objects.equals(previous.id(),node.id()))throw new IllegalArgumentException("同轮正式公司身份冲突");
            catalogue.put(node.code(),node);
        }
        Set<String> seen=new HashSet<>();Map<String,Binding> prior=new HashMap<>();
        batch.bindings().forEach(binding->{if(prior.put(binding.sourceKey(),binding)!=null)throw new IllegalArgumentException("公司来源别名重复");});
        List<Change> changes=new ArrayList<>();
        for(var row:batch.rows()){
            String raw=Objects.toString(row.fields().get("sourceCompanyCode"),null);
            if(!"COMPANY_REFERENCE".equals(row.object()) || !Objects.equals(row.sourceKey(),Objects.toString(row.fields().get("sourceCompanyId"),null))
                    || !seen.add(row.sourceKey()))throw new IllegalArgumentException("原公司来源身份缺失或重复");
            String code=DppmsCompanyCodeConverter.fromSource(raw);var company=catalogue.get(code);
            if(company==null)throw new IllegalArgumentException("原公司码转换后没有正式公司，不创建临时公司");
            var before=prior.get(row.sourceKey());
            if(row.targetId()!=null && !Objects.equals(row.targetId(),company.id())
                    || before!=null && (!Objects.equals(before.targetId(),company.id()) || !Objects.equals(before.lastFields().get("sourceCompanyCode"),raw)))
                throw new IllegalArgumentException("原公司来源别名已变化，不能改绑");
            Map<String,Object> after=new LinkedHashMap<>();after.put("sourceCompanyId",row.sourceKey());after.put("sourceCompanyCode",raw);
            after.put("companyCode",code);after.put("companyName",company.name());
            changes.add(new Change(row.object(),row.sourceKey(),company.id(),before==null?"REFERENCED":"UNCHANGED",
                    before==null?Map.of():before.lastFields(),after,"仅登记已有正式公司的原来源别名，不创建公司或授权"));
        }
        return List.copyOf(changes);
    }
}
