package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.*;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor
public class CollectionConnectionService {
    private final DeviceCredentialMapper credentials;
    private final CredentialGrantMapper grants;
    private final CollectionConnectionMapper queries;
    private final CollectionTemplateService templates;
    private final CollectionAuthorization auth;
    private final ProjectDeviceSelectionApi devices;
    private final AdminUserApi users;
    private final ObjectProvider<DeviceOpsResourceApi> resources;
    private final PlatformTransactionManager transactions;

    public List<View> owned(Long actor,Long project) {
        auth.permission(actor,"pms:device-credential:query");auth.project(actor,project,false,false);
        return queries.owned(new CollectionConnectionQuery(tenant(),actor,project,null,null,null,LocalDateTime.now())).stream().map(this::view).toList();
    }
    public List<View> usable(Long actor,Long project,Long device,String protocol,Long template) {
        auth.permission(actor,"pms:device-credential:use");auth.project(actor,project,false,false);
        if(device==null)return List.of();devices.validateSelection(project,List.of(device));
        return queries.usable(new CollectionConnectionQuery(tenant(),actor,project,device,protocol,template == null ? "*" : template.toString(),LocalDateTime.now())).stream().map(this::view).toList();
    }
    public View save(Long actor,Save request) {
        try {
            auth.permission(actor,"pms:device-credential:create");validate(request);auth.project(actor,request.projectId(),true,false);
            var selected=devices.validateSelection(request.projectId(),List.of(request.deviceId())).getFirst();
            String external=UUID.nameUUIDFromBytes((tenant()+":"+actor+":"+request.requestKey()).getBytes(StandardCharsets.UTF_8)).toString();
            DeviceCredentialDO pending=new TransactionTemplate(transactions).execute(tx->{
                auth.project(actor,request.projectId(),true,true);
                if(request.templateId()!=null)templates.forExecution(actor,request.templateId(),"center",request.protocol(),selected.productModel());
                var existing=queries.byExternal(tenant(),external);
                if(existing!=null){requireSame(existing,actor,request);return existing;}
                var row=new DeviceCredentialDO();row.setTenantId(tenant());row.setCreator(actor.toString());
                row.setCredentialCode(request.name().trim());row.setCredentialType(request.protocol());row.setUsername(request.username().trim());
                row.setExternalConnectionId(external);row.setRegistrationDigest(registrationDigest(request));row.setProjectId(request.projectId());row.setDeviceId(request.deviceId());
                row.setRegistrationKey(request.requestKey());row.setRegistrationTemplateId(request.templateId());row.setRegistrationExpiresAt(request.expiresAt().withNano(0));
                row.setHost(request.host());row.setPort(request.port());row.setCredentialVersion(0L);row.setStatus("PENDING");
                credentials.insert(row);return row;
            });
            if("ACTIVE".equals(pending.getStatus()))return view(pending);
            if(!"PENDING".equals(pending.getStatus()))throw new CollectionOperationException("连接已停用，请新建连接");
            var saved=provider().saveConnection(new DeviceOpsResourceApi.ConnectionCommand(external,"npdms-"+pending.getId(),request.host(),request.port(),request.protocol(),request.username().trim(),request.secret()));
            return new TransactionTemplate(transactions).execute(tx->{
                var row=queries.lock(tenant(),pending.getId());requireSame(row,actor,request);
                if("ACTIVE".equals(row.getStatus()))return view(row);
                if(!"PENDING".equals(row.getStatus()))throw new CollectionOperationException("连接状态已变化");
                row.setCredentialVersion(saved.version());row.setStatus("ACTIVE");credentials.updateById(row);
                grant(row,actor,request.templateId(),request.expiresAt());return view(row);
            });
        }catch(DeviceOpsResourceApi.ResourceException failure){throw new CollectionOperationException(failure.getMessage());}finally{if(request!=null&&request.secret()!=null)Arrays.fill(request.secret(),'\0');}
    }
    public List<GrantView> grants(Long actor,Long id){var row=ownedRequired(actor,id,"pms:device-credential:grant");return queries.grants(new ConnectionGrantsQuery(tenant(),row.getId())).stream().map(this::grantView).toList();}
    public GrantView grant(Long actor,Long id,Long grantee,Long template,LocalDateTime expires) {
        return new TransactionTemplate(transactions).execute(tx->{
            var row=ownedRequired(actor,id,"pms:device-credential:grant");
            if(!"ACTIVE".equals(row.getStatus()))throw new CollectionOperationException("连接未启用");
            if(grantee==null||users.getUser(grantee)==null)throw new CollectionOperationException("被授权用户不存在");
            var device=devices.validateSelection(row.getProjectId(),List.of(row.getDeviceId())).getFirst();
            if(template!=null)templates.forExecution(actor,template,"center",row.getCredentialType(),device.productModel());
            return grantView(grant(row,grantee,template,expires));
        });
    }
    public void revoke(Long actor,Long id,Long grantId) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            ownedRequired(actor,id,"pms:device-credential:grant");var grant=grants.selectById(grantId);
            if(grant==null||!tenant().equals(grant.getTenantId())||!id.equals(grant.getCredentialId()))throw new CollectionOperationException("授权不存在");
            grant.setStatus("REVOKED");grants.updateById(grant);
        });
    }
    public void disable(Long actor,Long id) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{var row=ownedRequired(actor,id,"pms:device-credential:update");row.setStatus("DISABLED");credentials.updateById(row);});
    }
    public Resolved resolve(Long actor,Long id,Long project,Long device,String protocol,Long template) {
        auth.permission(actor,"pms:device-credential:use");
        var row=queries.lock(tenant(),id);
        if(row==null||!"ACTIVE".equals(row.getStatus())||!project.equals(row.getProjectId())||!device.equals(row.getDeviceId())||!protocol.equals(row.getCredentialType()))throw new CollectionOperationException("连接不适用于当前设备");
        var effective=queries.effectiveGrants(new EffectiveCredentialGrantQuery(tenant(),id,"USER",actor.toString(),device.toString(),protocol,template == null ? "*" : template.toString(),LocalDateTime.now()));
        if(effective.size()!=1||!project.toString().equals(effective.getFirst().getProjectId()))throw new CollectionOperationException("连接授权不存在、过期或冲突");
        DeviceOpsResourceApi.Connection remote;
        try{remote=provider().getConnection(row.getExternalConnectionId());}catch(DeviceOpsResourceApi.ResourceException failure){throw new CollectionOperationException(failure.getMessage());}
        if(!row.getHost().equals(remote.host())||!row.getPort().equals(remote.port())||!row.getUsername().equals(remote.username())||!row.getCredentialVersion().equals(remote.version())||!protocol.equals(remote.protocol()))throw new CollectionOperationException("DAC 连接已变化，请更新连接授权");
        return new Resolved(row,effective.getFirst().getId(),remote);
    }
    public boolean remainsAuthorized(CollectionTaskDO task) {
        if(!"SAVED_CREDENTIAL".equals(task.getCredentialMode()))return true;
        var row=credentials.selectByTenantAndId(task.getTenantId(),task.getCredentialId());
        var grant=grants.selectById(task.getGrantSnapshotId());
        return row!=null&&"ACTIVE".equals(row.getStatus())&&grant!=null&&task.getTenantId().equals(grant.getTenantId())
                &&row.getId().equals(grant.getCredentialId())&&"ACTIVE".equals(grant.getStatus())
                &&Objects.equals(task.getProjectId(),String.valueOf(row.getProjectId()))
                &&Objects.equals(task.getDeviceId(),String.valueOf(row.getDeviceId()))
                &&Objects.equals(task.getProtocol(),row.getCredentialType())
                &&Objects.equals(task.getHost(),row.getHost())&&Objects.equals(task.getPort(),row.getPort())
                &&"USER".equals(grant.getGranteeType())&&Objects.equals(task.getCreator(),grant.getGranteeId())
                &&Objects.equals(task.getProjectId(),grant.getProjectId())&&Objects.equals(task.getDeviceId(),grant.getDeviceId())
                &&Objects.equals(task.getProtocol(),grant.getProtocol())&&("*".equals(grant.getCommandTemplateId())||Objects.equals(task.getTemplateId(),grant.getCommandTemplateId()))
                &&grant.getExpiresAt()!=null&&grant.getExpiresAt().isAfter(LocalDateTime.now());
    }
    public boolean dispatchAuthorized(CollectionTaskDO task,String connectionId,Long version) {
        if(!remainsAuthorized(task))return false;
        var row=credentials.selectByTenantAndId(task.getTenantId(),task.getCredentialId());
        return Objects.equals(connectionId,row.getExternalConnectionId())&&Objects.equals(version,row.getCredentialVersion());
    }
    private CredentialGrantDO grant(DeviceCredentialDO row,Long actor,Long template,LocalDateTime expires) {
        if(expires==null||!expires.isAfter(LocalDateTime.now()))throw new CollectionOperationException("授权有效期须晚于当前时间");
        // A new unrestricted grant must not overlap an existing template-scoped grant.
        var matching=queries.grants(new ConnectionGrantsQuery(tenant(),row.getId())).stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()) && "USER".equals(g.getGranteeType())
                        && actor.toString().equals(g.getGranteeId())
                        && (g.getExpiresAt()==null || g.getExpiresAt().isAfter(LocalDateTime.now()))
                        && (template==null || "*".equals(g.getCommandTemplateId()) || template.toString().equals(g.getCommandTemplateId())))
                .toList();
        if(!matching.isEmpty())throw new CollectionOperationException("相同使用范围已有有效授权，请先撤销旧授权");
        var g=new CredentialGrantDO();g.setTenantId(tenant());g.setCredentialId(row.getId());g.setGranteeType("USER");g.setGranteeId(actor.toString());g.setProjectId(row.getProjectId().toString());g.setDeviceId(row.getDeviceId().toString());g.setProtocol(row.getCredentialType());g.setCommandTemplateId(template == null ? "*" : template.toString());g.setExpiresAt(expires.withNano(0));g.setStatus("ACTIVE");grants.insert(g);return g;
    }
    private DeviceCredentialDO ownedRequired(Long actor,Long id,String permission){auth.permission(actor,permission);var row=queries.lock(tenant(),id);if(row==null||!actor.toString().equals(row.getCreator())||row.getExternalConnectionId()==null)throw new org.springframework.security.access.AccessDeniedException("只能管理本人创建的连接");auth.project(actor,row.getProjectId(),true,false);return row;}
    private void requireSame(DeviceCredentialDO r,Long actor,Save s){if(r==null||!actor.toString().equals(r.getCreator())||!registrationDigest(s).equals(r.getRegistrationDigest()))throw new CollectionOperationException("原保存请求内容已变化");}
    private String registrationDigest(Save s){return CollectionTemplateService.hash(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(Arrays.asList(s.name().trim(),s.projectId(),s.deviceId(),s.host(),s.port(),s.protocol(),s.username().trim(),s.templateId(),s.expiresAt().withNano(0))));}
    private void validate(Save s){if(s==null||s.requestKey()==null||!s.requestKey().matches("[a-zA-Z0-9-]{16,64}")||s.name()==null||s.name().isBlank()||s.name().length()>64||s.projectId()==null||s.deviceId()==null||s.host()==null||!s.host().matches("[a-zA-Z0-9.:-]{1,253}")||s.port()==null||s.port()<1||s.port()>65535||s.protocol()==null||!Set.of("SSH","TELNET").contains(s.protocol())||s.username()==null||s.username().isBlank()||s.username().length()>128||s.secret()==null||s.secret().length==0||s.secret().length>4096||s.expiresAt()==null||!s.expiresAt().isAfter(LocalDateTime.now()))throw new CollectionOperationException("请填写完整有效的连接和授权信息");}
    private DeviceOpsResourceApi provider(){var value=resources.getIfAvailable();if(value==null)throw new CollectionOperationException("DAC 尚未启用");return value;}
    private Long tenant(){return CollectionAuthorization.tenant();}
    private View view(DeviceCredentialDO r){boolean pending="PENDING".equals(r.getStatus());return new View(r.getId(),r.getCredentialCode(),r.getProjectId(),r.getDeviceId(),r.getCredentialType(),r.getHost(),r.getPort(),r.getUsername(),r.getStatus(),r.getCredentialVersion(),pending?r.getRegistrationKey():null,pending?r.getRegistrationTemplateId():null,pending?r.getRegistrationExpiresAt():null);}
    private GrantView grantView(CredentialGrantDO g){return new GrantView(g.getId(),g.getGranteeId(),g.getDeviceId(),g.getProtocol(),g.getCommandTemplateId(),g.getExpiresAt(),"ACTIVE".equals(g.getStatus())&&g.getExpiresAt()!=null&&!g.getExpiresAt().isAfter(LocalDateTime.now())?"EXPIRED":g.getStatus());}
    public record Save(String requestKey,String name,Long projectId,Long deviceId,String host,Integer port,String protocol,String username,@com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) char[] secret,Long templateId,LocalDateTime expiresAt){}
    public record View(Long id,String name,Long projectId,Long deviceId,String protocol,String host,Integer port,String username,String status,Long version,String registrationKey,Long registrationTemplateId,LocalDateTime registrationExpiresAt){}
    public record GrantView(Long id,String granteeId,String deviceId,String protocol,String templateId,LocalDateTime expiresAt,String status){}
    public record Resolved(DeviceCredentialDO credential,Long grantId,DeviceOpsResourceApi.Connection connection){}
}
