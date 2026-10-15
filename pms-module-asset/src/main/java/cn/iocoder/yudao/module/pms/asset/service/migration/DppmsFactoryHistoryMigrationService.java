package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsFactoryHistoryMigrationApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceFactoryGlobalRecordQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DppmsFactoryHistoryMigrationService implements DppmsFactoryHistoryMigrationApi {
    private final DeviceFactoryVersionRecordMapper records;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Result> preview(Command command) {return execute(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) {return execute(command,true);}
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0 || command.entries()==null || command.entries().size()>500)
            throw new IllegalArgumentException("DPPMS_FACTORY_HISTORY_SCOPE_INVALID");
        users.validateUser(command.actorId());
        if(!permissions.hasAnyPermissions(command.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        var keys=new HashSet<String>();var ids=new HashSet<Long>();var results=new ArrayList<Result>();
        var creates=new ArrayList<DeviceFactoryVersionRecordDO>();long previewId=-1;
        for(var entry:command.entries()) {
            if(entry==null || entry.sourceKey()==null || !entry.sourceKey().matches("[0-9a-f]{64}:[1-9][0-9]*")
                    || !keys.add(entry.sourceKey()) || entry.values()==null
                    || entry.mappedRecordId()!=null && (entry.mappedRecordId()<=0 || !ids.add(entry.mappedRecordId())))
                throw new IllegalArgumentException("DPPMS_FACTORY_HISTORY_IDENTITY_INVALID");
            var values=entry.values();
            for(String value:Arrays.asList(values.serialNumber(),values.conp(),values.cpld(),values.boot(),values.pcb()))
                if(value!=null && value.length()>100)throw new IllegalArgumentException("DPPMS_FACTORY_HISTORY_VALUE_TOO_LONG");
            if(entry.mappedRecordId()!=null) {
                var existing=records.selectGlobalRecordForUpdate(new DeviceFactoryGlobalRecordQuery(command.tenantId(),entry.mappedRecordId()));
                if(existing==null || Boolean.TRUE.equals(existing.getDeleted()) || existing.getProjectId()!=null
                        || !Objects.equals(existing.getTenantId(),command.tenantId())
                        || !values.equals(new Values(existing.getDeviceSn(),existing.getConpVersion(),existing.getCpldVersion(),existing.getBootVersion(),existing.getPcbVersion())))
                    throw new IllegalArgumentException("DPPMS_FACTORY_HISTORY_TARGET_CHANGED");
                results.add(new Result(entry.sourceKey(),existing.getId(),"UNCHANGED",values));
            } else {
                var row=new DeviceFactoryVersionRecordDO();row.setId(apply?IdWorker.getId():previewId--);
                row.setTenantId(command.tenantId());row.setDeviceSn(values.serialNumber());row.setConpVersion(values.conp());
                row.setCpldVersion(values.cpld());row.setBootVersion(values.boot());row.setPcbVersion(values.pcb());
                creates.add(row);results.add(new Result(entry.sourceKey(),row.getId(),"CREATED",values));
            }
        }
        if(apply)for(var row:creates)if(records.insert(row)!=1)throw new IllegalStateException("DPPMS_FACTORY_HISTORY_INSERT_FAILED");
        return List.copyOf(results);
    }
}
