package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonToken;

/** Whole-table initial license history, with one-row approval verification and authenticated encryption. */
@Component
public class P37LicenseHistorySyncTask extends AbstractSyncTask<P37LicenseHistorySyncTask.LicenseSource,P37LicenseHistorySyncTask.Parameters> {
    private final TargetMapper licenses;private final SecretKeySpec key;private final String keyId;private final Path approvedFacts;
    private final SecureRandom random=new SecureRandom();
    public P37LicenseHistorySyncTask(TargetMapper licenses,@Value("${pms.license-history.master-key:}") String encoded,
            @Value("${pms.license-history.key-id:V1}") String keyId,@Value("${pms.base-sync.license-approved-facts:}") String approvedFacts){
        this.licenses=licenses;
        if(keyId==null || !keyId.matches("[A-Za-z0-9._-]{1,64}"))throw new IllegalArgumentException("LICENSE_KEY_ID_INVALID");
        this.keyId=keyId;this.approvedFacts=approvedFacts==null || approvedFacts.isBlank()?null:Path.of(approvedFacts);
        if(encoded==null || encoded.isBlank()){key=null;return;}
        byte[] decoded=null;
        try{decoded=Base64.getDecoder().decode(encoded);if(decoded.length!=32)throw new IllegalArgumentException();key=new SecretKeySpec(decoded,"AES");}
        catch(RuntimeException failure){throw new IllegalArgumentException("LICENSE_MASTER_KEY_INVALID");}
        finally{if(decoded!=null)Arrays.fill(decoded,(byte)0);}
    }
    public static final class Parameters {long devicePending,contractPending;}
    @Override protected Parameters createParameters(SyncContext<?> runtime){return new Parameters();}
    @Override public String taskCode(){return "BASE_P37";}
    @Override public String taskName(){return "整表许可历史";}
    @Override public void work(SyncContext<Parameters> context){
        if(key==null)throw new IllegalStateException("LICENSE_MASTER_KEY_UNAVAILABLE");
        if(approvedFacts==null || !Files.isRegularFile(approvedFacts))throw new IllegalStateException("P37已批准非秘密来源清单未配置");
        syncData(context.forData("LicenseHistory",false,true));
        if(context.parameters().devicePending>0)context.runState().warning("P37_DEVICE_ASSOCIATION_PENDING:"+context.parameters().devicePending);
        if(context.parameters().contractPending>0)context.runState().warning("P37_CONTRACT_ASSOCIATION_PENDING:"+context.parameters().contractPending);
    }
    @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> context,ResultHandler<LicenseSource> handler){
        // Consume the immutable non-secret approval alongside the source; neither full input is retained.
        try(var approved=new ApprovalCursor(approvedFacts)){
            super.syncDataQueryByRowHandler(context,result->{
                var row=result.getResultObject();
                try{
                    if(row.getSourceId()==null || !Objects.equals(approved.next(),fact(row)))throw new IllegalStateException("P37来源身份或七项批准原值已变化");
                    row.setEnvelope(encrypt(new Capture(context.tenantId(),row.getSourceId().toString(),row.getLicenseCode()==null?null:row.getLicenseCode().toCharArray())));
                    if(row.getLicenseCode().isEmpty())context.runState().warning("P37_LICENSE_CODE_EMPTY:sourceId="+row.getSourceId());
                }finally{row.setLicenseCode(null);}
                handler.handleResult(result);
            });
            approved.finish();
        }catch(IOException failure){throw new IllegalStateException("P37批准清单不可读取");}
    }
    @Override protected void syncDataInsertBefore(SyncBatch<LicenseSource,Parameters> batch){
        var context=batch.context();var keys=batch.rows().stream().map(row->row.getSourceId().toString()).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("LICENSE_HISTORY",keys));
        var targets=bindings.isEmpty()?List.<LicenseTarget>of():licenses.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,LicenseTarget>();for(var row:targets)if(byId.putIfAbsent(row.getId(),row)!=null)throw new IllegalStateException("P37目标身份重复");
        for(var source:batch.rows()){
            for(var value:Arrays.asList(source.getSn(),source.getItem(),source.getStatus()))checkTextLength(value,255);
            checkTextLength(source.getSpecModel(),256);checkTextLength(source.getContract(),32);checkTextLength(source.getContractType(),16);
            var binding=bindings.get(source.getSourceId().toString());var old=binding==null?null:byId.get(binding.targetId());
            if(binding!=null){
                if(old==null || old.getTenantId()!=context.tenantId() || Boolean.TRUE.equals(old.getDeleted())
                        || !Objects.equals(binding.fingerprint(),fingerprint(source,old))
                        || !source.getEnvelope().codeMac().equals(verifyStored(old.getLicenseCodeCiphertext(),context.tenantId(),source.getSourceId().toString()))
                        || !sourceValues(source).equals(business(old)))throw new IllegalStateException("P37不可变许可历史或来源绑定已变化");
                source.setTarget(old);source.setSyncAction(SyncWriteAction.UNCHANGED);continue;
            }
            var row=new LicenseTarget();row.setId(context.preview()?null:IdWorker.getId());row.setTenantId(context.tenantId());
            row.setDeviceSn(source.getSn());row.setProductModel(source.getSpecModel());row.setContractNo(source.getContract());
            row.setContractType(source.getContractType());row.setProductCode(source.getItem());row.setLicenseStatus(source.getStatus());
            // Nullable business IDs are intentionally deferred. Original SN/contract fields carry the complete history.
            context.parameters().devicePending++;context.parameters().contractPending++;
            row.setLicenseCodeCiphertext(JsonUtils.toJsonString(new Sealed("AES-256-GCM",source.getEnvelope())));
            row.setCreator(Long.toString(context.actorId()));row.setUpdater(row.getCreator());row.setCreateTime(context.startedAt());row.setUpdateTime(context.startedAt());row.setDeleted(false);
            source.setTarget(row);source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<LicenseSource,Parameters> batch){return SyncCounts.fromActions(batch.rows(),LicenseSource::getSyncAction);}
    @Override protected void syncDataInsert(SyncBatch<LicenseSource,Parameters> batch){super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));}
    @Override protected void syncDataInsertAfter(SyncBatch<LicenseSource,Parameters> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save("LICENSE_HISTORY",row.getSourceId().toString(),row.getTarget().getId(),fingerprint(row,row.getTarget()),null)).toList());
    }
    public record Approved(String id,String sn,String specModel,String contract,String contractType,String item,String status){}
    static Approved fact(LicenseSource row){return new Approved(Objects.toString(row.getSourceId(),null),row.getSn(),row.getSpecModel(),row.getContract(),row.getContractType(),row.getItem(),row.getStatus());}
    static List<Object> sourceValues(LicenseSource row){return Arrays.asList(row.getSn(),row.getSpecModel(),row.getContract(),row.getContractType(),row.getItem(),row.getStatus());}
    static List<Object> business(LicenseTarget row){return Arrays.asList(row.getDeviceSn(),row.getProductModel(),row.getContractNo(),row.getContractType(),row.getProductCode(),row.getLicenseStatus());}
    static String fingerprint(LicenseSource source,LicenseTarget row){return SyncFingerprint.of(Arrays.asList(source.getSourceId(),source.getEnvelope().codeMac(),row.getId(),row.getTenantId(),row.getDeviceId(),row.getContractId(),row.getDeviceSn(),row.getContractNo(),row.getContractType(),row.getProductCode(),row.getProductModel(),row.getLicenseStatus(),row.getCreator(),row.getUpdater(),row.getCreateTime(),row.getUpdateTime(),row.getDeleted(),row.getLicenseCodeCiphertext()));}
    static final class ApprovalCursor implements AutoCloseable {
        final tools.jackson.databind.json.JsonMapper mapper=tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        final tools.jackson.core.JsonParser parser;long count,previous;
        ApprovalCursor(Path path)throws IOException{
            parser=mapper.createParser(new GZIPInputStream(Files.newInputStream(path)));
            if(parser.nextToken()!=JsonToken.START_ARRAY){parser.close();throw new IllegalStateException("P37批准清单格式无效");}
        }
        Approved next(){
            if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IllegalStateException("P37来源多于批准清单");
            var node=mapper.readTree(parser);
            if(node.size()!=7 || !node.propertyNames().equals(Set.of("id","sn","specModel","contract","contractType","item","status")))
                throw new IllegalStateException("P37批准清单字段无效");
            var fact=mapper.treeToValue(node,Approved.class);long id=Long.parseLong(fact.id());
            if(id<=previous || ++count>475468)throw new IllegalStateException("P37批准清单身份或容量无效");previous=id;return fact;
        }
        void finish(){if(count!=475468 || parser.nextToken()!=JsonToken.END_ARRAY || parser.nextToken()!=null)throw new IllegalStateException("P37必须完整读取475468条批准来源");}
        @Override public void close(){parser.close();}
    }
    record Capture(long tenantId,String sourceKey,char[] code){}
    Envelope encrypt(Capture input){
        byte[] plaintext=null,aad=null,nonce=new byte[12],encrypted=null,macBytes=null;ByteBuffer buffer=null;
        try{
            if(key==null || input==null || input.tenantId()<=0 || input.sourceKey()==null || !input.sourceKey().matches("[1-9][0-9]*")
                    || input.code()==null || Character.codePointCount(input.code(),0,input.code().length)>256)throw new IllegalArgumentException();
            buffer=StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(input.code()));plaintext=new byte[buffer.remaining()];buffer.get(plaintext);
            aad=aad(input.tenantId(),input.sourceKey());random.nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad);encrypted=cipher.doFinal(plaintext);
            var mac=Mac.getInstance("HmacSHA256");mac.init(key);mac.update(aad);mac.update((byte)0);macBytes=mac.doFinal(plaintext);
            var payload=ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();
            return new Envelope(input.sourceKey(),Base64.getEncoder().encodeToString(payload),keyId,HexFormat.of().formatHex(macBytes));
        }catch(Exception failure){throw new IllegalArgumentException("P37_LICENSE_CAPTURE_ENCRYPTION_FAILED");}
        finally{
            if(input!=null && input.code()!=null)Arrays.fill(input.code(),'\0');
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);if(aad!=null)Arrays.fill(aad,(byte)0);Arrays.fill(nonce,(byte)0);
            if(encrypted!=null)Arrays.fill(encrypted,(byte)0);if(macBytes!=null)Arrays.fill(macBytes,(byte)0);
            if(buffer!=null && buffer.hasArray())Arrays.fill(buffer.array(),(byte)0);
        }
    }
    record Sealed(String algorithm,Envelope envelope){}
    String verifyStored(String value,long tenant,String sourceKey){
        byte[] payload=null,plaintext=null,expected=null,actual=null;
        try{
            var sealed=JsonUtils.parseObject(value,Sealed.class);var envelope=sealed.envelope();
            if(!"AES-256-GCM".equals(sealed.algorithm()) || !keyId.equals(envelope.keyId()) || !sourceKey.equals(envelope.sourceKey()))throw new IllegalArgumentException();
            payload=Base64.getDecoder().decode(envelope.ciphertext());if(payload.length<28 || payload.length>1052)throw new IllegalArgumentException();
            var aad=aad(tenant,sourceKey);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,payload,0,12));cipher.updateAAD(aad);plaintext=cipher.doFinal(payload,12,payload.length-12);
            var mac=Mac.getInstance("HmacSHA256");mac.init(key);mac.update(aad);mac.update((byte)0);expected=mac.doFinal(plaintext);actual=HexFormat.of().parseHex(envelope.codeMac());
            if(!MessageDigest.isEqual(expected,actual))throw new IllegalArgumentException();return envelope.codeMac();
        }catch(Exception failure){throw new IllegalStateException("P37_LICENSE_STORED_AUTHENTICATION_FAILED");}
        finally{for(var bytes:Arrays.asList(payload,plaintext,expected,actual))if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    static byte[] aad(long tenant,String sourceKey){return ("DPPMS-LICENSE|"+tenant+"|1|"+sourceKey).getBytes(StandardCharsets.UTF_8);}
    @lombok.Getter @lombok.Setter public static class LicenseSource {
        private Long sourceId;
        private String sn;
        private String specModel;
        private String contract;
        private String contractType;
        private String item;
        private String status;
        @JsonIgnore private String licenseCode;
        @JsonIgnore private Envelope envelope;
        @JsonIgnore private LicenseTarget target;
        private SyncWriteAction syncAction;
    }
    @lombok.Getter @lombok.Setter public static class LicenseTarget {
        private Long id;
        private Long tenantId;
        private Long deviceId;
        private Long contractId;
        private String deviceSn;
        private String contractNo;
        private String contractType;
        private String productCode;
        private String productModel;
        private String licenseStatus;
        private String creator;
        private String updater;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean deleted;
        @JsonIgnore private String licenseCodeCiphertext;
    }
    public interface SourceMapper {void selectLicenseHistory(SourceQuery<Parameters> query,ResultHandler<? super LicenseSource> handler);}
    @Mapper public interface TargetMapper {
        record ExistingQuery(long tenantId,List<Long> ids,boolean preview){}
        List<LicenseTarget> selectExisting(@Param("query") ExistingQuery query);
        int insertLicenseHistory(SyncBatch<LicenseSource,Parameters> batch);
    }
}
