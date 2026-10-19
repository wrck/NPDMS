package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import java.util.*;

/** Fixed, reviewed migration groups. One task/run/transaction; original Owner validation remains in each adapter. */
final class DppmsConsolidation {
    private DppmsConsolidation() {}
    record OrganizationPreviewBatch(DataSyncAdapter.Batch batch,
            List<cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result> preceding,Set<String> canonicalWinners) {
        OrganizationPreviewBatch(DataSyncAdapter.Batch batch,List<cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result> preceding){this(batch,preceding,null);}
    }
    static final Map<String,List<String>> GROUPS=Map.of(
            "DPPMS_ORGANIZATION",List.of("EHR_ORGANIZATION","DPPMS_DEPARTMENTS","DPPMS_EHR_DEPARTMENT_COMPLETION","DPPMS_COMPANY_REFERENCES"),
            "DPPMS_DICTIONARIES",List.of("DPPMS_DICTIONARY_TYPES","DPPMS_DICTIONARY_DATA"));
    static boolean accepts(SyncTaskDO task,String component) {
        if(component.equals(task.getAdapter()))return true;
        boolean accountFoundation = Set.of("DPPMS_ACCOUNT_FOUNDATION",DppmsDirectAccountTemplate.ADAPTER).contains(task.getAdapter())
                && Set.of("DPPMS_USERS", "DPPMS_ROLES", "DPPMS_USER_ROLES", "DPPMS_USER_SCOPES").contains(component);
        boolean dictionaries=DppmsDirectDictionaryTemplate.ADAPTER.equals(task.getAdapter())
                && Set.of("DPPMS_DICTIONARY_TYPES","DPPMS_DICTIONARY_DATA").contains(component);
        if(!accountFoundation && !dictionaries && !GROUPS.getOrDefault(task.getAdapter(),List.of()).contains(component))return false;
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        return definition!=null && task.getAdapter().equals(definition.adapter()) && "DPPMS".equals(task.getSourceSystem())
                && "ONCE".equals(definition.mode()) && "RETAIN".equals(definition.missingPolicy())
                && !definition.clearBeforeLoad() && !definition.resetMappingsBeforeLoad();
    }
    static DataSyncAdapter bind(String key,Map<String,DataSyncAdapter> providers) {
        var components=GROUPS.get(key).stream().map(providers::get).toList();
        if(components.stream().anyMatch(Objects::isNull))throw new IllegalStateException("合并迁移缺少既有Owner适配器");
        return new DataSyncAdapter() {
            public Descriptor descriptor() {
                return new Descriptor(key,"DPPMS_ORGANIZATION".equals(key)?"DPPMS公司、部门与主管关联":"DPPMS基础字典类型与字典项",
                        components.stream().flatMap(a->a.descriptor().objects().stream()).map(object->{
                            if(!"DPPMS_ORGANIZATION".equals(key) || "COMPANY_REFERENCE".equals(object.name()))return object;
                            var fields=new ArrayList<>(object.fields());
                            DppmsOrganizationProjection.METADATA.forEach(field->fields.add(new Field(field,field,"STRING",false)));
                            return new ObjectDescriptor(object.name(),object.label(),fields,object.targetContext(),object.targetObjectType(),object.targetTable(),object.supportsSourcePrimaryKey());
                        }).toList(),List.of("RETAIN"),List.of("UPSERT"),false);
            }
            public boolean sharesTargetAcrossSources(){return true;}
            public List<Change> preview(Batch batch){return execute(batch,false);}
            public List<Change> apply(Batch batch){return execute(batch,true);}
            public void refreshCaches(){components.forEach(DataSyncAdapter::refreshCaches);}
            private List<Change> execute(Batch batch,boolean apply) {
                if(!batch.full() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
                    throw new IllegalArgumentException("合并迁移必须完整读取并保留既有数据");
                var changes=new ArrayList<Change>();
                var canonical="DPPMS_ORGANIZATION".equals(key)?DppmsOrganizationCanonical.from(batch):null;
                List<cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result> organizationPreview=List.of();
                for(var component:components) {
                    var objects=component.descriptor().objects().stream().map(ObjectDescriptor::name).collect(java.util.stream.Collectors.toSet());
                    var selected=new Batch(batch.owner(),batch.rows().stream().filter(r->objects.contains(r.object())).toList(),
                            batch.bindings().stream().filter(b->objects.contains(b.object())).toList(),true,batch.missingPolicy(),
                            batch.adoptExisting(),batch.loadingMode(),false);
                    List<Change> result;
                    if(canonical!=null && component instanceof EhrOrganizationAdapter ehr) {
                        var ehrBatch=canonical.ehrBatch(selected);
                        var plan=apply?ehr.applyPlan(ehrBatch):ehr.previewPlan(ehrBatch);
                        organizationPreview=plan.organization();result=plan.changes();
                    } else if(canonical!=null && component instanceof DppmsDepartmentSyncAdapter departments)
                        result=departments.executeCanonical(new DppmsOrganizationCanonical.ComponentBatch(selected,apply,canonical.winners(),organizationPreview));
                    else if(canonical!=null && component instanceof DppmsEhrDepartmentCompletionAdapter completion)
                        result=completion.executeCanonical(new DppmsOrganizationCanonical.ComponentBatch(selected,apply,canonical.winners(),organizationPreview));
                    else if(!apply && component instanceof EhrOrganizationAdapter ehr) {
                        var preview=ehr.previewPlan(selected);organizationPreview=preview.organization();result=preview.changes();
                    } else if(!apply && component instanceof DppmsDepartmentSyncAdapter departments)
                        result=departments.previewAfterOrganization(new OrganizationPreviewBatch(selected,organizationPreview));
                    else if(!apply && component instanceof DppmsEhrDepartmentCompletionAdapter completion)
                        result=completion.previewAfterOrganization(new OrganizationPreviewBatch(selected,organizationPreview));
                    else if(!apply && component instanceof DppmsCompanyReferenceSyncAdapter references)
                        result=references.previewAfterOrganization(new OrganizationPreviewBatch(selected,organizationPreview));
                    else result=apply?component.apply(selected):component.preview(selected);
                    if(canonical!=null && component instanceof DppmsCompanyReferenceSyncAdapter
                            && changes.stream().noneMatch(c->Set.of("ISSUE","CONFLICT").contains(c.action()))) {
                        var aliased=canonical.aliases(new DppmsOrganizationCanonical.Receipts(changes,organizationPreview));changes.clear();changes.addAll(aliased);
                    }
                    changes.addAll(result);
                    if(result.stream().anyMatch(c->Set.of("ISSUE","CONFLICT").contains(c.action())))break;
                }
                return List.copyOf(changes);
            }
        };
    }
}
