package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTemplateQuery;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service @RequiredArgsConstructor
public class CollectionTemplateService {
    private final CollectionTemplateMapper templates;
    private final CollectionAuthorization auth;
    private final ObjectProvider<DeviceOpsResourceApi> resources;
    private final PlatformTransactionManager transactions;
    public List<View> list(Long actor,String purpose,String protocol,boolean publishedOnly) {
        auth.permission(actor,publishedOnly?"pms:collection-template:use":"pms:collection-template:query");
        return templates.list(new CollectionTemplateQuery(CollectionAuthorization.tenant(),purpose,protocol,publishedOnly)).stream().map(this::view).toList();
    }
    public View save(Long actor,Draft draft) {
        auth.permission(actor,draft.id()==null?"pms:collection-template:create":"pms:collection-template:update");
        validate(draft);
        return new TransactionTemplate(transactions).execute(tx -> {
            var row=draft.id()==null?new CollectionTemplateDO():locked(draft.id());
            if(draft.id()!=null && (!"DRAFT".equals(row.getStatus()) || Boolean.TRUE.equals(row.getPublicationStarted()) || !Objects.equals(row.getVersion(),draft.version()))) throw new CollectionOperationException("模板已开始发布或版本已变化，请新建版本");
            if(draft.id()!=null && (!row.getTemplateCode().equals(draft.code()) || !row.getRevision().equals(draft.revision()))) throw new CollectionOperationException("版本标识不可修改");
            row.setTenantId(CollectionAuthorization.tenant());row.setTemplateCode(draft.code());row.setName(draft.name().trim());
            row.setPurpose(draft.purpose());row.setProtocol(draft.protocol());row.setDeviceModel(blank(draft.deviceModel())?null:draft.deviceModel().trim());row.setRevision(draft.revision());
            row.setOwnerContext(switch(draft.purpose()) {case "configuration","joint-test" -> "IMP";case "cutover" -> "CUT";case "inspection" -> "SRV";default -> "PLT";});
            row.setCommandText(normalize(draft.commands()));row.setContentHash(hash(row.getCommandText()));row.setStatus("DRAFT");
            if(draft.id()==null) {row.setCreator(actor.toString());row.setPublicationStarted(false);row.setVersion(0L);templates.insert(row);} else if(templates.updateById(row)!=1) throw new CollectionOperationException("模板版本冲突");
            return view(row);
        });
    }
    public View publish(Long actor,Long id,Long version) {
        auth.permission(actor,"pms:collection-template:publish");
        var provider=resources.getIfAvailable();if(provider==null) throw new CollectionOperationException("DAC 尚未启用");
        // Freeze content durably before external I/O. A lost registration response may already have created an immutable artifact.
        var frozen=new TransactionTemplate(transactions).execute(tx -> {
            var row=locked(id);
            if("PUBLISHED".equals(row.getStatus())) return row;
            if(!"DRAFT".equals(row.getStatus()) || (!Boolean.TRUE.equals(row.getPublicationStarted()) && !Objects.equals(version,row.getVersion()))) throw new CollectionOperationException("模板状态或版本已变化");
            if(!Boolean.TRUE.equals(row.getPublicationStarted())) {
                row.setPublicationStarted(true);
                if(templates.updateById(row)!=1) throw new CollectionOperationException("模板发布冲突");
            }
            return row;
        });
        if("PUBLISHED".equals(frozen.getStatus())) return view(frozen);
        try{provider.registerScript("plt-"+frozen.getId(),frozen.getRevision().toString(),frozen.getCommandText(),frozen.getContentHash());}catch(DeviceOpsResourceApi.ResourceException failure){throw new CollectionOperationException(failure.getMessage());}
        return new TransactionTemplate(transactions).execute(tx -> {
            var row=locked(id);
            if("PUBLISHED".equals(row.getStatus())) return view(row);
            if(!"DRAFT".equals(row.getStatus()) || !Boolean.TRUE.equals(row.getPublicationStarted())) throw new CollectionOperationException("模板状态已变化");
            row.setStatus("PUBLISHED");row.setPublishedAt(LocalDateTime.now());
            if(templates.updateById(row)!=1) throw new CollectionOperationException("模板发布冲突");return view(row);
        });
    }
    public View retire(Long actor,Long id,Long version) {
        auth.permission(actor,"pms:collection-template:publish");
        return new TransactionTemplate(transactions).execute(tx -> {
            var row=locked(id);if("RETIRED".equals(row.getStatus())) return view(row);
            if(!"PUBLISHED".equals(row.getStatus()) || !Objects.equals(version,row.getVersion())) throw new CollectionOperationException("仅当前发布版本可停用");
            row.setStatus("RETIRED");if(templates.updateById(row)!=1) throw new CollectionOperationException("模板状态冲突");return view(row);
        });
    }
    public CollectionTemplateDO forExecution(Long actor,Long id,String entry,String protocol,String deviceModel) {
        auth.permission(actor,"pms:collection-template:use");var row=locked(id);
        if(!"PUBLISHED".equals(row.getStatus()) || (!"center".equals(entry)&&!entry.equals(row.getPurpose()))
                || !row.getProtocol().equals(protocol) || (!blank(row.getDeviceModel())&&!row.getDeviceModel().equals(deviceModel))) throw new CollectionOperationException("模板未发布、已停用或不适用于当前设备及入口");
        return row;
    }
    private CollectionTemplateDO locked(Long id) {var row=templates.lockById(CollectionAuthorization.tenant(),id);if(row==null)throw new CollectionOperationException("模板不存在");return row;}
    private View view(CollectionTemplateDO r){return new View(r.getId(),r.getTemplateCode(),r.getName(),r.getPurpose(),r.getOwnerContext(),r.getProtocol(),r.getDeviceModel(),r.getRevision(),r.getCommandText(),r.getStatus(),r.getVersion(),Boolean.TRUE.equals(r.getPublicationStarted()));}
    private void validate(Draft d){if(d.code()==null||!d.code().matches("[A-Za-z0-9-]{1,64}")||blank(d.name())||d.name().length()>128||d.revision()==null||d.revision()<1||d.purpose()==null||d.protocol()==null||!Set.of("configuration","joint-test","center","cutover","inspection").contains(d.purpose())||!Set.of("SSH","TELNET").contains(d.protocol())||(d.deviceModel()!=null&&d.deviceModel().length()>128))throw new CollectionOperationException("模板参数无效");normalize(d.commands());}
    public static String normalize(String commands){if(blank(commands)||commands.length()>65536||commands.indexOf('\0')>=0)throw new CollectionOperationException("请填写有效命令");return String.join("\n",commands.replace("\r\n","\n").replace('\r','\n').lines().filter(line->!line.isBlank()).toList());}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static boolean blank(String s){return s==null||s.isBlank();}
    public record Draft(Long id,Long version,String code,String name,String purpose,String protocol,String deviceModel,Integer revision,String commands){}
    public record View(Long id,String code,String name,String purpose,String ownerContext,String protocol,String deviceModel,Integer revision,String commands,String status,Long version,boolean publicationStarted){}
}
