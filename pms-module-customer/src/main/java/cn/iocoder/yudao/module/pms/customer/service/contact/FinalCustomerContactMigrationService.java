package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.FinalCustomerContactMigrationMapper;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 专用历史Owner：完整联系事实写入CUS，不创建客户主档、权限或普通完成事件。 */
@Service @RequiredArgsConstructor
public class FinalCustomerContactMigrationService implements FinalCustomerContactMigrationApi {
    private final ProjectContactContextApi projects;
    private final FinalCustomerContactMigrationMapper contacts;

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Preview preview(Query query) {
        var party=requireParty(query);
        var rows=rows(query);
        return new Preview(new FinalCustomer(party.id(),party.projectId(),party.name(),party.sourceProjectKey()),fingerprint(rows),rows.size());
    }

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Result carry(Command command) {
        if(command==null || command.contacts()==null || command.contacts().isEmpty() || command.contacts().size()>200)
            fail("SOURCE_CONTACTS_INVALID");
        var query=command.query();var party=requireParty(query);var before=rows(query);
        if(!Objects.equals(party.id(),command.projectPartyId()) || !fingerprint(before).equals(command.existingContactsChecksum()))
            fail("TARGET_CHANGED_SINCE_PREVIEW");
        var expected=new TreeMap<Long,ProjectCustomerContactDO>();before.forEach(row->expected.put(row.getId(),cn.hutool.core.bean.BeanUtil.copyProperties(row,ProjectCustomerContactDO.class)));
        var keys=new HashSet<String>();var result=new ArrayList<Target>();
        for(var source:command.contacts()) {
            validateSource(source,party,keys);
            var row=new ProjectCustomerContactDO();row.setId(IdWorker.getId());row.setTenantId(query.tenantId());row.setProjectId(query.projectId());
            row.setProjectPartyId(party.id());row.setContactCode(source.contactCode());row.setName(source.name());row.setPhone(source.phone());
            row.setEmail(source.email());row.setRegistrationSource(source.registrationSource());row.setEffectiveFrom(source.effectiveFrom());row.setEffectiveTo(source.effectiveTo());
            row.setCreator(source.creator());row.setCreateTime(source.createTime());row.setUpdater(source.updater());
            row.setUpdateTime(source.updateTime()==null?source.createTime():source.updateTime());
            row.setPrimaryFlag(false);row.setStatus(0);row.setVersion(0);row.setDeleted(false);
            var preserved=cn.hutool.core.bean.BeanUtil.copyProperties(row,ProjectCustomerContactDO.class);
            if(contacts.insertHistorical(new FinalCustomerContactMigrationMapper.HistoricalInsert(row))!=1)fail("INSERT_FAILED");
            expected.put(preserved.getId(),preserved);result.add(new Target(source.sourceKey(),preserved.getId(),fingerprint(preserved)));
        }
        var actual=new TreeMap<Long,ProjectCustomerContactDO>();rows(query).forEach(row->actual.put(row.getId(),row));
        if(!expected.equals(actual)) fail("FORMAL_FIELDS_DIFFER");
        return new Result(List.copyOf(result));
    }

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void verify(Query query,Result result) {
        requireParty(query);
        if(result==null || result.targets()==null)fail("REPLAY_RESULT_INVALID");
        var actual=new HashMap<Long,ProjectCustomerContactDO>();rows(query).forEach(row->actual.put(row.getId(),row));
        for(var target:result.targets()) {
            var row=actual.get(target.id());
            if(row==null || !Objects.equals(target.formalFieldsChecksum(),fingerprint(row)))fail("REPLAY_CONTACT_CHANGED");
        }
    }

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void validateContacts(Command command) {
        if(command==null || command.contacts()==null || command.contacts().isEmpty() || command.contacts().size()>200)fail("SOURCE_CONTACTS_INVALID");
        var party=requireParty(command.query());var before=rows(command.query());
        if(!Objects.equals(party.id(),command.projectPartyId()) || !fingerprint(before).equals(command.existingContactsChecksum()))fail("TARGET_CHANGED_SINCE_PREVIEW");
        var keys=new HashSet<String>();for(var source:command.contacts())validateSource(source,party,keys);
    }
    private static void validateSource(HistoricalContact source,ProjectContactContextApi.FinalCustomer party,Set<String> keys) {
            if(source==null || source.sourceKey()==null || source.sourceKey().isBlank() || !keys.add(source.sourceKey())
                    || !Objects.equals(party.sourceProjectKey(),source.sourceProjectKey())
                    || !"10".equals(source.projectType()) || !"60".equals(source.memberRole())
                    || source.name()==null || source.name().isBlank() || source.name().length()>64
                    || source.createTime()==null || source.createTime().getNano()!=0
                    || source.updateTime()!=null && source.updateTime().getNano()!=0
                    || source.effectiveFrom()!=null && source.effectiveTo()!=null && source.effectiveTo().isBefore(source.effectiveFrom()))
                fail("SOURCE_CONTACT_INVALID");
    }

    private ProjectContactContextApi.FinalCustomer requireParty(Query query) {
        if(query==null || query.tenantId()==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.projectId()==null || query.expectedProjectVersion()==null)fail("QUERY_INVALID");
        var party=projects.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(query.tenantId(),query.actorId(),query.projectId(),query.expectedProjectVersion()));
        if(party==null || party.id()==null || !Objects.equals(party.projectId(),query.projectId()))fail("FINAL_CUSTOMER_NOT_VERIFIED");
        return party;
    }
    private List<ProjectCustomerContactDO> rows(Query query) {
        return contacts.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(query.tenantId(),query.projectId()));
    }
    static String fingerprint(Object value) {return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));}
    private static void fail(String reason){throw new IllegalArgumentException("DPPMS_FINAL_CONTACT_"+reason);}
}
