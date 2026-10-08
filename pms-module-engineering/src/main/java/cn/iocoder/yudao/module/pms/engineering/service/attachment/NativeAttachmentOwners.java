package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.externalprocurement.ExternalProcurementMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.MaterialRequisitionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.attachment.query.NativeAttachmentOwnerLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class NativeAttachmentOwners {
    private final ConfigurationMapper configuration;
    private final JointTestMapper jointtest;
    private final ExternalProcurementMapper externalprocurement;
    private final OutsourceRequestMapper outsource;
    private final MaterialRequisitionMapper materialrequisition;
    private final MaterialExchangeMapper materialexchange;
    public record Owner(Long id,Long tenantId,Long projectId,Integer status) { }
    public Owner find(NativeAttachmentKind kind,Long tenant,Long id,boolean lock) {
        var query=new NativeAttachmentOwnerLockQuery(tenant,id);
        return switch(kind) {
            case CONFIGURATION -> { var row=lock?configuration.selectAttachmentOwnerForUpdate(query):configuration.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
            case JOINT_TEST -> { var row=lock?jointtest.selectAttachmentOwnerForUpdate(query):jointtest.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
            case EXTERNAL_PROCUREMENT -> { var row=lock?externalprocurement.selectAttachmentOwnerForUpdate(query):externalprocurement.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
            case OUTSOURCE -> { var row=lock?outsource.selectAttachmentOwnerForUpdate(query):outsource.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
            case MATERIAL_REQUISITION -> { var row=lock?materialrequisition.selectAttachmentOwnerForUpdate(query):materialrequisition.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
            case MATERIAL_EXCHANGE -> { var row=lock?materialexchange.selectAttachmentOwnerForUpdate(query):materialexchange.selectById(id); yield row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus()); }
        };
    }
}
