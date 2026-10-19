package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectLicenseHistoryTemplate {
    static final String ADAPTER="DPPMS_SECURE_LICENSE_DIRECT",OBJECT=DppmsSecureLicenseSyncTemplate.OBJECT;
    static final java.util.List<String> ORIGINAL=DppmsSecureLicenseSyncTemplate.ORIGINAL,COLUMNS=DppmsSecureLicenseSyncTemplate.COLUMNS;
    static java.util.List<DppmsSecureLicenseSyncTemplate.Approved> approved(SyncDefinition.Source source) {
        return DirectLicenseApprovalManifest.load(source).facts();
    }
    static SyncDefinition.Source approvedSource(DppmsSecureLicenseSyncTemplate.Request request) {
        if(request==null || request.connectionId()==null || request.connectionId()<=0
                || request.ownerProjectId()!=null || request.approvedLicenses()==null)
            throw new IllegalArgumentException("LICENSE_APPROVAL_INVALID");
        var selected=request.approvedLicenses().stream().sorted(java.util.Comparator.comparing(a->new java.math.BigInteger(a.id()))).toList();
        var source=DppmsSecureLicenseSyncTemplate.create(new DppmsSecureLicenseSyncTemplate.Request(request.connectionId(),request.ownerProjectId(),java.util.List.of())).sources().getFirst();
        return source.toBuilder().parameters(java.util.Map.of("approvedLicenseIds",String.join(",",selected.stream().map(DppmsSecureLicenseSyncTemplate.Approved::id).toList()),
                "approvedLicenseFacts",DirectLicenseApprovalManifest.encode(selected),"ownerProjectId",java.util.Objects.toString(request.ownerProjectId(),""))).build();
    }
    private DppmsDirectLicenseHistoryTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("受控许可历史来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P37-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("受控许可历史直写配置缺失",error);}
    }
}
