package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import java.util.*;

/** Invocation-local selection and alias receipts. No formal IDs are allocated here. */
final class DppmsOrganizationCanonical {
    record ComponentBatch(DataSyncAdapter.Batch batch,boolean apply,Set<String> winners,List<ManagedOrganizationApi.Result> preceding) {}
    private final Map<String,DataSyncAdapter.Row> rows=new LinkedHashMap<>();
    private final Set<String> winners=new HashSet<>();
    private final Map<String,DataSyncAdapter.Binding> prior=new HashMap<>();
    private DppmsOrganizationCanonical(DataSyncAdapter.Batch batch) {
        for(var row:batch.rows())if(!"COMPANY_REFERENCE".equals(row.object())) {
            if(!Set.of("COMPANY","DEPARTMENT","fnd_department","ehr_department").contains(row.object())
                    || !row.fields().keySet().containsAll(DppmsOrganizationProjection.METADATA) || rows.put(key(row),row)!=null)
                throw new IllegalArgumentException("组织归并不得混入无主定义的原行或重复身份");
        }
        for(var row:rows.values()) {
            String code=code(row),winnerKey=winnerKey(row);var winner=rows.get(winnerKey);
            if(code==null || !Objects.equals(code,row.fields().get("_organizationBusinessCode")) || winner==null
                    || !Objects.equals(code,code(winner)) || !Objects.equals(winnerKey,winnerKey(winner))
                    || "COMPANY".equals(row.object()) != "COMPANY".equals(winner.object()))
                throw new IllegalArgumentException("组织原行不能指向其他业务编码或不存在的主定义");
            winners.add(winnerKey);
        }
        for(var binding:batch.bindings())if(prior.put(binding.object()+":"+binding.sourceKey(),binding)!=null)
            throw new IllegalArgumentException("组织来源绑定重复");
    }
    static DppmsOrganizationCanonical from(DataSyncAdapter.Batch batch) {
        return batch.rows().stream().anyMatch(row->row.fields().containsKey("_organizationWinnerObject"))?new DppmsOrganizationCanonical(batch):null;
    }
    Set<String> winners(){return Collections.unmodifiableSet(winners);}
    DataSyncAdapter.Batch ehrBatch(DataSyncAdapter.Batch batch) {
        return new DataSyncAdapter.Batch(batch.owner(),batch.rows().stream().filter(row->winners.contains(key(row))).map(row->{
                    var fields=new LinkedHashMap<>(row.fields());
                    if("DEPARTMENT".equals(row.object())){fields.put("companyKey",fields.get("_organizationCompanyKey"));fields.put("parentKey",fields.get("_organizationParentKey"));}
                    return new DataSyncAdapter.Row(row.object(),row.sourceKey(),fields,row.targetId());
                }).toList(),
                batch.bindings().stream().filter(binding->winners.contains(binding.object()+":"+binding.sourceKey())).toList(),
                batch.full(),batch.missingPolicy(),batch.adoptExisting(),batch.loadingMode(),batch.clearBeforeLoad());
    }
    record Receipts(List<DataSyncAdapter.Change> changes,List<ManagedOrganizationApi.Result> organization) {}
    List<DataSyncAdapter.Change> aliases(Receipts input) {
        var changes=input.changes();var organization=input.organization();
        Map<String,DataSyncAdapter.Change> receipts=new HashMap<>();
        for(var change:changes)if(receipts.put(change.object()+":"+change.sourceKey(),change)!=null)
            throw new IllegalArgumentException("组织主定义Owner回执重复");
        Map<String,ManagedOrganizationApi.Result> nodes=new HashMap<>();organization.forEach(result->nodes.put(result.key(),result));
        List<DataSyncAdapter.Change> result=new ArrayList<>();
        for(var row:rows.values()) {
            var receipt=receipts.get(winnerKey(row));var binding=prior.get(key(row));
            if(receipt==null || Set.of("ISSUE","CONFLICT","SKIPPED").contains(receipt.action()))
                throw new IllegalArgumentException("组织主定义Owner未通过，不能登记别名");
            if(row.targetId()!=null && !Objects.equals(row.targetId(),receipt.targetId())
                    || binding!=null && !Objects.equals(binding.targetId(),receipt.targetId()))
                throw new IllegalArgumentException("组织来源原绑定与业务编码主定义不同，不能改绑");
            Map<String,Object> after=new LinkedHashMap<>();
            if(winners.contains(key(row)))after.putAll(receipt.after());
            else if(Set.of("fnd_department","ehr_department").contains(row.object())) {
                SystemDepartmentMigrationApi.Department department=department(new DepartmentReceipt(receipt,nodes,false));
                if(department==null)throw new IllegalArgumentException("组织主定义缺少正式部门回执");
                if(binding!=null && !Boolean.TRUE.equals(binding.lastFields().get("_referenceOnly"))) {
                    var last=binding.lastFields().get("department");var before=department(new DepartmentReceipt(receipt,nodes,true));
                    if(last==null || !Objects.equals(JsonUtils.getObjectMapper().convertValue(last,SystemDepartmentMigrationApi.Department.class),before))
                        throw new IllegalArgumentException("原承接部门已被维护，不能用归并别名隐藏历史冲突");
                }
                after.put("department",department);after.put("_referenceOnly",true);
            } else {
                if(binding!=null && !Objects.equals(binding.lastFields().get("code"),code(row)))
                    throw new IllegalArgumentException("组织别名原业务编码变化，不能改绑");
                after.putAll(row.fields());after.put("_referenceOnly",true);
                for(String field:List.of("_companyTargetId","_parentTargetId"))if(receipt.after().containsKey(field))after.put(field,receipt.after().get(field));
            }
            for(String field:DppmsOrganizationProjection.METADATA)after.put(field,row.fields().get(field));
            if("ehr_department".equals(row.object()))after.put("_directorSourceEmployeeId",row.fields().get("director"));
            result.add(new DataSyncAdapter.Change(row.object(),row.sourceKey(),receipt.targetId(),winners.contains(key(row))?receipt.action():
                    binding==null?"REFERENCED":"UNCHANGED",winners.contains(key(row))?receipt.before():binding==null?Map.of():binding.lastFields(),after,
                    winners.contains(key(row))?receipt.message():"同一业务编码复用已校验的完整主定义；仅登记原来源别名"));
        }
        return List.copyOf(result);
    }
    private record DepartmentReceipt(DataSyncAdapter.Change receipt,Map<String,ManagedOrganizationApi.Result> nodes,boolean before) {}
    private static SystemDepartmentMigrationApi.Department department(DepartmentReceipt input) {
        var receipt=input.receipt();var nodes=input.nodes();boolean before=input.before();
        Object raw=(before?receipt.before():receipt.after()).get("department");
        if(raw!=null)return JsonUtils.getObjectMapper().convertValue(raw,SystemDepartmentMigrationApi.Department.class);
        var result=nodes.get(receipt.object()+":"+receipt.sourceKey());var node=result==null?null:before?result.before():result.after();
        if(node==null || node.department()==null)return null;
        var d=node.department();return new SystemDepartmentMigrationApi.Department(d.getId(),d.getCode(),d.getName(),d.getParentId(),d.getSort(),d.getLeaderUserId(),
                d.getPhone(),d.getEmail(),d.getStatus(),d.getVersion(),d.getCreator(),d.getCreateTime(),d.getUpdater(),d.getUpdateTime());
    }
    private static String code(DataSyncAdapter.Row row){return Objects.toString(row.fields().get(switch(row.object()) {
        case "COMPANY","DEPARTMENT" -> "code";case "fnd_department" -> "departmentNum";case "ehr_department" -> "depCode";default -> "";}),null);}
    private static String winnerKey(DataSyncAdapter.Row row){return row.fields().get("_organizationWinnerObject")+":"+row.fields().get("_organizationWinnerKey");}
    private static String key(DataSyncAdapter.Row row){return row.object()+":"+row.sourceKey();}
}
