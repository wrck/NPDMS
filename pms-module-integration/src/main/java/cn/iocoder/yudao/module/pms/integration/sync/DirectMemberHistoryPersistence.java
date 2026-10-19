package cn.iocoder.yudao.module.pms.integration.sync;
import static cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectMemberHistoryRow;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectMemberHistoryMapper;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.*;

/** New source entry; the existing explicit authorization and primary-takeover path remains separate. */
@RequiredArgsConstructor
final class DirectMemberHistoryPersistence {
    private final javax.sql.DataSource dataSource;
    private final DirectProjectAccess access;
    private final DirectProjectMapper projects;
    private final DirectMemberHistoryMapper members;
    private final SystemUserMigrationApi identities;
    private final DirectContactHistoryPersistence contacts;
    private final PermissionApi permissions;
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command){return execute(command,false);}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command){return execute(command,true);}
    private List<Result> execute(Command q,boolean apply) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("DPPMS_MEMBER_TRANSACTION_REQUIRED");
        if(q==null || !Objects.equals(q.tenantId(),TenantContextHolder.getRequiredTenantId()) || q.actorId()==null || q.actorId()<=0
                || q.projectId()==null || q.entries()==null || q.entries().isEmpty() || q.entries().size()>200)fail("COMMAND_INVALID");
        if(!permissions.hasAnyPermissions(q.actorId(),"pms:integration:execute"))throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        access.getProjectForManage(q.projectId(),new DirectProjectAccess.Actor(q.tenantId(),q.actorId()));
        var project=projects.selectIdentityForUpdate(new DirectProjectMapper.IdentityQuery(q.tenantId(),q.projectId()));
        if(project==null || !Objects.equals(project.getTenantId(),q.tenantId()) || Boolean.TRUE.equals(project.getDeleted())
                || !Set.of("ACTIVE","NORMAL_CLOSED","EXCEPTION_CLOSED").contains(project.getLifecycleStatus()) || !"10".equals(project.getProjectType()))fail("PROJECT_INACCESSIBLE");
        var names=new LinkedHashSet<String>();var keys=new HashSet<String>();
        for(var e:q.entries()) {
            if(entryInvalid(e))fail("SOURCE_INVALID");
            var raw=e.originalFields();
            if(raw==null || !raw.keySet().equals(new HashSet<>(COLUMNS)) || !Objects.equals(e.sourceKey(),raw.get("id"))
                    || !keys.add(e.sourceKey()) || !"10".equals(raw.get("projectType"))
                    || !Set.of("10","20","30","60").contains(raw.get("memberRole"))
                    || (e.targetId()==null)!=(e.formalFieldsJson()==null))fail("SOURCE_INVALID");
            for(String field:List.of("memberCode","createBy","updateBy"))if(raw.get(field)!=null && !raw.get(field).isEmpty())names.add(raw.get(field));
            if(date(raw.get("effectiveFrom"))!=null && date(raw.get("effectiveTo"))!=null
                    && date(raw.get("effectiveTo")).isBefore(date(raw.get("effectiveFrom"))))fail("SOURCE_INTERVAL_INVALID");
            for(String field:List.of("createTime","updateTime"))if(date(raw.get(field))!=null && date(raw.get(field)).getNano()!=0)fail("AUDIT_PRECISION_INVALID");
        }
        var accounts=new HashMap<String,SystemUserMigrationApi.MemberIdentity>();var all=new ArrayList<>(names);
        for(int from=0;from<all.size();from+=200)for(var account:identities.getHistoricalMemberIdentities(q.tenantId(),Set.copyOf(all.subList(from,Math.min(from+200,all.size())))))
            if(!names.contains(account.username()) || accounts.putIfAbsent(account.username(),account)!=null)fail("ACCOUNT_AMBIGUOUS");
        // Audit references must resolve; a contact/member subject is allowed to have no account.
        for(var entry:q.entries())for(String field:List.of("createBy","updateBy"))actor(entry.originalFields().get(field),accounts);
        var current=members.selectProjectForUpdate(new DirectMemberHistoryMapper.ProjectQuery(q.tenantId(),q.projectId()));
        var byId=new HashMap<Long,DirectMemberHistoryRow>();current.forEach(row->byId.put(row.getId(),row));
        var result=new LinkedHashMap<String,Result>();var fresh=new ArrayList<DirectMemberHistoryRow>();
        var contactQuery=new FinalCustomerContactMigrationApi.Query(q.tenantId(),q.actorId(),q.projectId(),project.getVersion());
        var freshContacts=new ArrayList<FinalCustomerContactMigrationApi.HistoricalContact>();var oldContacts=new ArrayList<FinalCustomerContactMigrationApi.Target>();
        long virtual=-1;
        for(var entry:q.entries()) {
            var raw=entry.originalFields();
            if("60".equals(raw.get("memberRole"))) {
                if(entry.targetId()!=null) {
                    var target=JsonUtils.parseObject(entry.formalFieldsJson(),FinalCustomerContactMigrationApi.Target.class);
                    if(!Objects.equals(target.id(),entry.targetId()) || !Objects.equals(target.sourceKey(),entry.sourceKey()))fail("BOUND_TARGET_CHANGED");
                    oldContacts.add(target);result.put(entry.sourceKey(),new Result(entry.sourceKey(),entry.targetId(),"cus_project_customer_contact_relation","UNCHANGED",entry.formalFieldsJson()));
                } else freshContacts.add(new FinalCustomerContactMigrationApi.HistoricalContact(entry.sourceKey(),raw.get("projectId"),raw.get("projectType"),raw.get("memberRole"),
                    raw.get("memberCode"),raw.get("memberName"),raw.get("phoneNum"),raw.get("email"),raw.get("fromFlag"),date(raw.get("createTime")),actor(raw.get("createBy"),accounts),
                    date(raw.get("updateTime")),actor(raw.get("updateBy"),accounts),date(raw.get("effectiveFrom")),date(raw.get("effectiveTo"))));
                continue;
            }
            if(entry.targetId()!=null) {
                var row=byId.get(entry.targetId());
                if(row==null || !Objects.equals(JsonUtils.toJsonString(row),entry.formalFieldsJson()))fail("BOUND_MEMBER_CHANGED");
                result.put(entry.sourceKey(),new Result(entry.sourceKey(),row.getId(),"proj_project_member_assignment","UNCHANGED",entry.formalFieldsJson()));continue;
            }
            var row=new DirectMemberHistoryRow();row.setId(apply?IdWorker.getId():virtual--);row.setTenantId(q.tenantId());row.setProjectId(q.projectId());row.setDeleted(false);
            var account=accounts.get(raw.get("memberCode"));row.setUserId(account==null?null:account.id());row.setEmployeeNo(account==null?null:account.employeeNo());
            row.setMemberCode(raw.get("memberCode"));row.setMemberName(raw.get("memberName"));row.setPhoneNumber(raw.get("phoneNum"));row.setEmail(raw.get("email"));row.setRegistrationSource(raw.get("fromFlag"));
            row.setMemberRole(switch(raw.get("memberRole")){case "10"->SALES_REPRESENTATIVE;case "20"->SERVICE_MANAGER;case "30"->PROJECT_MANAGER;default->throw new IllegalArgumentException();});
            row.setAuthorizationConfirmed(false);row.setStatus("ACTIVE");row.setVersion(0);row.setEffectiveFrom(date(raw.get("effectiveFrom")));row.setEffectiveTo(date(raw.get("effectiveTo")));
            row.setCreator(actor(raw.get("createBy"),accounts));row.setUpdater(actor(raw.get("updateBy"),accounts));row.setCreateTime(date(raw.get("createTime")));
            row.setUpdateTime(date(raw.get("updateTime"))==null?row.getCreateTime():date(raw.get("updateTime")));
            row.setCompanyId(project.getCompanyId());row.setCompanyCode(project.getCompanyCode());row.setCompanyName(project.getCompanyName());fresh.add(row);
            result.put(entry.sourceKey(),new Result(entry.sourceKey(),apply?row.getId():null,"proj_project_member_assignment","CREATED",JsonUtils.toJsonString(row)));
        }
        var intervals=new ArrayList<>(current);intervals.addAll(fresh);validateIntervals(intervals);
        if(!oldContacts.isEmpty())contacts.verify(contactQuery,new FinalCustomerContactMigrationApi.Result(oldContacts));
        if(!freshContacts.isEmpty()) {
            var preview=contacts.preview(contactQuery);
            var command=new FinalCustomerContactMigrationApi.Command(contactQuery,preview.finalCustomer().id(),preview.existingContactsChecksum(),freshContacts);
            contacts.validateContacts(command);
            if(apply)for(var target:contacts.carry(command).targets()) {
                if(!keys.contains(target.sourceKey()) || result.putIfAbsent(target.sourceKey(),new Result(target.sourceKey(),target.id(),"cus_project_customer_contact_relation","CREATED",JsonUtils.toJsonString(target)))!=null)fail("CONTACT_RECEIPT_INVALID");
            }
            else for(var source:freshContacts)result.put(source.sourceKey(),new Result(source.sourceKey(),null,"cus_project_customer_contact_relation","CREATED",null));
        }
        if(result.size()!=q.entries().size())fail("OWNER_RECEIPT_INCOMPLETE");
        if(apply) {
            for(var row:fresh)if(members.insertHistorical(new DirectMemberHistoryMapper.HistoricalInsert(row))!=1)fail("INSERT_FAILED");
            var actual=new HashMap<Long,DirectMemberHistoryRow>();members.selectProjectForUpdate(new DirectMemberHistoryMapper.ProjectQuery(q.tenantId(),q.projectId())).forEach(row->actual.put(row.getId(),row));
            if(actual.size()!=current.size()+fresh.size())fail("EXISTING_MEMBER_FIELDS_DIFFER");
            for(var row:current)if(!Objects.equals(row,actual.get(row.getId())))fail("EXISTING_MEMBER_FIELDS_DIFFER");
            for(var row:fresh)if(!Objects.equals(row,actual.get(row.getId())))fail("INSERTED_FIELDS_DIFFER");
        }
        return q.entries().stream().map(e->result.get(e.sourceKey())).toList();
    }
    private static boolean entryInvalid(Entry entry) {
        return entry==null || entry.sourceKey()==null || !entry.sourceKey().matches("[1-9][0-9]*")
            || entry.targetId()!=null && entry.targetId()<=0;
    }
    private static String actor(String value,Map<String,SystemUserMigrationApi.MemberIdentity> accounts) {
        if(value==null || value.isEmpty())return value;var account=accounts.get(value);if(account==null)fail("AUDIT_ACCOUNT_PENDING");return account.id().toString();
    }
    static void validateIntervals(List<DirectMemberHistoryRow> rows) {
        for(var row:rows) if(row.getEffectiveFrom()!=null && row.getEffectiveTo()!=null && row.getEffectiveTo().isBefore(row.getEffectiveFrom()))
            fail("SOURCE_INTERVAL_INVALID");
        for(int i=0;i<rows.size();i++)for(int j=i+1;j<rows.size();j++) {
            var a=rows.get(i);var b=rows.get(j);
            if(a.getUserId()!=null && Objects.equals(a.getUserId(),b.getUserId()) && normalize(a.getMemberRole()).equals(normalize(b.getMemberRole()))
                    && a.getEffectiveFrom()!=null && b.getEffectiveFrom()!=null
                    && (a.getEffectiveTo()==null || b.getEffectiveFrom().isBefore(a.getEffectiveTo()))
                    && (b.getEffectiveTo()==null || a.getEffectiveFrom().isBefore(b.getEffectiveTo())))fail("SOURCE_INTERVAL_OVERLAP");
        }
    }
    private static LocalDateTime date(String value) {
        return value==null || value.isBlank()?null:LocalDateTime.parse(value.replace(' ','T'));
    }
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_MEMBER_SOURCE_"+code);}
}
