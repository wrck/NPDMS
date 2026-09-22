package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.CustomerMasterDataApi;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CrmCustomerSyncAdapter implements DataSyncAdapter {
    public static final String KEY="CRM_CUSTOMER";
    private final CustomerMasterDataApi customers;
    private static final List<Field> FIELDS=List.of(
            required("customerCode"),required("customerName"),optional("shortName"),optional("contactPhone"),optional("contactEmail"),optional("address"),
            optional("departmentCode"),optional("departmentName"),optional("marketCode"),optional("marketName"),optional("systemCode"),optional("systemName"),
            optional("expendCode"),optional("expendName"),optional("industryCode"),optional("industryName"),required("lifecycleStatus"),required("sourceVersion"),required("dataAsOf"),optional("migrationIssue"));
    @Override public Descriptor descriptor(){return new Descriptor(KEY,"CRM客户主数据",List.of(new ObjectDescriptor("CUSTOMER","客户",FIELDS,"CUS","cus_customer_master","cus_customer_master",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public boolean supportsStreaming(){return true;}
    @Override public void refreshCaches(){}
    @Override public List<Change> preview(Batch batch){return batch.rows().stream().map(r->{
        String issue=text(r.fields(),"migrationIssue");
        return new Change(r.object(),r.sourceKey(),r.targetId(),issue==null?(r.targetId()==null?"CREATED":"UPDATED"):"ISSUE",Map.of(),r.fields(),issue);
    }).toList();}
    @Override public List<Change> apply(Batch batch){
        long tenant=TenantContextHolder.getRequiredTenantId();List<Change> out=new ArrayList<>();
        for(var row:batch.rows()){
            String issue=text(row.fields(),"migrationIssue");
            if(issue!=null){out.add(new Change(row.object(),row.sourceKey(),row.targetId(),"ISSUE",Map.of(),row.fields(),issue));continue;}
            var f=row.fields();String sourceVersion=text(f,"sourceVersion");
            var result=customers.apply(new CustomerMasterDataCommand(tenant,row.targetId(),text(f,"customerCode"),text(f,"customerName"),text(f,"shortName"),
                    text(f,"contactPhone"),text(f,"contactEmail"),text(f,"address"),text(f,"departmentCode"),text(f,"departmentName"),text(f,"marketCode"),text(f,"marketName"),
                    text(f,"systemCode"),text(f,"systemName"),text(f,"expendCode"),text(f,"expendName"),text(f,"industryCode"),text(f,"industryName"),text(f,"lifecycleStatus"),
                    row.sourceKey(),sourceVersion,LocalDateTime.parse(text(f,"dataAsOf").replace(' ','T')),"CRM:"+row.sourceKey()+":"+sourceVersion,null));
            out.add(new Change(row.object(),row.sourceKey(),result.customerId(),result.replayed()?"UNCHANGED":row.targetId()==null?"CREATED":"UPDATED",Map.of(),f,null));
        }
        return out;
    }
    private static Field required(String name){return new Field(name,name,"STRING",true);}
    private static Field optional(String name){return new Field(name,name,"STRING",false);}
    private static String text(Map<String,Object> fields,String name){Object value=fields.get(name);return value==null?null:value.toString();}
}
