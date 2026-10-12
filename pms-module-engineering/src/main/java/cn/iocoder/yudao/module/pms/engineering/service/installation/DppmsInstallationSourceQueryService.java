package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
/** Reads adjudicated history from the existing installation business entity. */
@Service @RequiredArgsConstructor
public class DppmsInstallationSourceQueryService implements DppmsInstallationSourceQueryApi {
    private final InstallationMapper records;
    private final ProjectScopeApi scope;
    private final DeviceQueryApi devices;
    @Override public PageResult<SourceRecord> pageSourceRecords(Query query) {
        if(!Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_INSTALLATION_HISTORY_TENANT_MISMATCH");
        if(query.actorId()==null || query.pageNo()<1 || query.pageSize()<1 || query.pageSize()>100)
            throw new IllegalArgumentException("DPPMS_INSTALLATION_HISTORY_QUERY_INVALID");
        Set<Long> visible=scope.resolveAllCurrent(new ProjectAllScopeQuery(query.tenantId(),query.actorId(),ProjectScopeApi.ACTION_VIEW));
        if(visible==null || visible.isEmpty() || query.projectId()!=null&&!visible.contains(query.projectId()))return new PageResult<>(List.of(),0L);
        var page=records.selectFormalHistoryPage(new InstallationSourceHistoryPageQuery(query.tenantId(),visible,query.projectId(),query.equipmentId(),query.pageNo(),query.pageSize()));
        var result=new ArrayList<SourceRecord>();
        for(var row:page.getList()) {
            if(!Objects.equals(row.getTenantId(),query.tenantId()) || !visible.contains(row.getProjectId())
                    || query.projectId()!=null&&!Objects.equals(query.projectId(),row.getProjectId())
                    || query.equipmentId()!=null&&!Objects.equals(query.equipmentId(),row.getEquipmentId())
                    || row.getCode()==null || !row.getCode().matches("DPPMS-[0-9]{1,32}"))
                throw new IllegalStateException("DPPMS_INSTALLATION_HISTORY_OWNER_SCOPE_MISMATCH");
            var device=devices.getDevice(row.getEquipmentId());String sn=device==null?null:device.sn();String key=row.getCode().substring(6);
            var history=new HistoricalInstallation(row.getId(),row.getProjectId(),row.getEquipmentId(),row.getCode(),key,sn,null,true,
                    row.getStatus(),row.getInstallLocation(),row.getAddressId(),row.getAddressVersion(),row.getLocationResolutionStatus(),
                    Objects.equals(row.getStatus(),2)&&row.getEffectiveTo()==null?row.getEquipmentId():null,row.getEffectiveFrom(),row.getEffectiveTo(),row.getVersion());
            var audit=new DppmsInstallationAddressImportApi.Details(null,null,null,null,null,null,null,null,null,null,null,null,null,
                    row.getCreateTime(),row.getCreator(),row.getUpdateTime(),row.getUpdater());
            result.add(new SourceRecord(row.getId(),row.getProjectId(),row.getEquipmentId(),key,sn,null,null,null,row.getInstallLocation(),
                    row.getAddressId(),row.getAddressVersion(),row.getLocationResolutionStatus(),audit,history));
        }
        return new PageResult<>(List.copyOf(result),page.getTotal());
    }
}
