package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LicenseBaseSyncTest {
    @TempDir Path directory;
    P37LicenseHistorySyncTask task(Path approval){return new P37LicenseHistorySyncTask(mock(P37LicenseHistorySyncTask.TargetMapper.class),Base64.getEncoder().encodeToString(new byte[32]),"TEST",approval.toString());}
    String seal(Envelope envelope){return JsonUtils.toJsonString(new P37LicenseHistorySyncTask.Sealed("AES-256-GCM",envelope));}
    @Test void originalEmptyStringIsAuthenticatedAndNullIsNeverConvertedToEmpty(){
        var task=task(directory);char[] empty={};var envelope=task.encrypt(new P37LicenseHistorySyncTask.Capture(1,"472122",empty));
        assertEquals(28,Base64.getDecoder().decode(envelope.ciphertext()).length);
        assertEquals(envelope.codeMac(),task.verifyStored(seal(envelope),1,"472122"));
        assertThrows(IllegalArgumentException.class,()->task.encrypt(new P37LicenseHistorySyncTask.Capture(1,"472122",null)));
    }
    @Test void authenticatedEnvelopeRejectsCrossTenantChangedIdentityOrTamperingAndWipesInput(){
        var task=task(directory);char[] code="synthetic-secret-中文".toCharArray();
        var encrypted=task.encrypt(new P37LicenseHistorySyncTask.Capture(1,"7",code));
        assertTrue(java.util.stream.IntStream.range(0,code.length).allMatch(i->code[i]==0));
        assertEquals(encrypted.codeMac(),task.verifyStored(seal(encrypted),1,"7"));
        assertThrows(IllegalStateException.class,()->task.verifyStored(seal(encrypted),2,"7"));
        assertThrows(IllegalStateException.class,()->task.verifyStored(seal(encrypted),1,"8"));
        var payload=Base64.getDecoder().decode(encrypted.ciphertext());payload[payload.length-1]^=1;
        var corrupted=new Envelope("7",Base64.getEncoder().encodeToString(payload),"TEST",encrypted.codeMac());
        assertThrows(IllegalStateException.class,()->task.verifyStored(seal(corrupted),1,"7"));
        var row=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(7L).setLicenseCode("synthetic-secret-中文").setEnvelope(encrypted);
        assertFalse(JsonUtils.toJsonString(row).contains("synthetic-secret"));assertFalse(row.toString().contains("synthetic-secret"));
    }
    @Test void initialLicenseHistoryPreservesBusinessKeysWithoutOptionalIdLookup(){
        var mapper=mock(P37LicenseHistorySyncTask.TargetMapper.class);
        var task=new P37LicenseHistorySyncTask(mapper,"","TEST",directory.toString());
        var row=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(7L).setSn("original-sn").setContract("original-contract")
                .setCapturedLicenseCode("synthetic").setCodeFingerprint(P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(1,"7","synthetic")));
        var identities=mock(SyncIdentityStore.class);when(identities.findByKeys(any())).thenReturn(Map.of());
        var runtime=new SyncContext<Void>(1,2,3,true,500,1000000,LocalDateTime.now(),mock(SyncSource.class),identities,mock(SyncReferences.class),"LicenseHistory",mock(SqlSession.class),false,true);
        var context=runtime.forParameters(task.createParameters(runtime));
        task.syncDataInsertBefore(new SyncBatch<>(context,List.of(row)));
        assertNull(row.getTarget().getDeviceId());assertNull(row.getTarget().getContractId());
        assertEquals("original-sn",row.getTarget().getDeviceSn());assertEquals("original-contract",row.getTarget().getContractNo());
        assertEquals("synthetic",row.getTarget().getLicenseCode());assertNull(row.getTarget().getLicenseCodeCiphertext());
        assertNull(row.getCapturedLicenseCode());assertFalse(JsonUtils.toJsonString(row.getTarget()).contains("synthetic"));
        assertEquals(1,context.parameters().devicePending);assertEquals(1,context.parameters().contractPending);
        verifyNoInteractions(mapper);
    }
    Path approval(String json)throws Exception{
        var path=directory.resolve("approval.json.gz");try(var gzip=new GZIPOutputStream(Files.newOutputStream(path))){gzip.write(json.getBytes(StandardCharsets.UTF_8));}return path;
    }
    @Test void captureClearsPlaintextBeforeCallingTheBatchAndRegistersEmptyIssueButRefusesPartialCoverage()throws Exception{
        var path=approval("[{\"id\":\"472122\",\"sn\":null,\"specModel\":null,\"contract\":null,\"contractType\":null,\"item\":\"产品\",\"status\":null}]");
        var task=task(path);var source=mock(SyncSource.class);var opened=mock(SyncSource.Session.class);var nativeSession=mock(SqlSession.class);
        when(source.open(3)).thenReturn(opened);when(opened.sqlSession()).thenReturn(nativeSession);
        var row=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(472122L).setItem("产品").setLicenseCode("");
        doAnswer(call->{ResultHandler<P37LicenseHistorySyncTask.LicenseSource> handler=call.getArgument(2);var result=mock(ResultContext.class);when(result.getResultObject()).thenReturn(row);handler.handleResult(result);return null;}).when(nativeSession).select(anyString(),any(),any(ResultHandler.class));
        var runtime=new SyncContext<Void>(1,2,3,true,500,1000000,LocalDateTime.now(),source,mock(SyncIdentityStore.class),mock(SyncReferences.class),"LicenseHistory",mock(SqlSession.class),false,true);
        var context=runtime.forParameters(task.createParameters(runtime));int[] called={0};
        assertThrows(IllegalStateException.class,()->task.syncDataQueryByRowHandler(context,result->{assertNull(result.getResultObject().getLicenseCode());assertEquals("",result.getResultObject().getCapturedLicenseCode());assertNotNull(result.getResultObject().getCodeFingerprint());assertNull(result.getResultObject().getEnvelope());called[0]++;}));
        assertEquals(1,called[0]);assertNull(row.getLicenseCode());assertEquals(List.of("P37_LICENSE_CODE_EMPTY:sourceId=472122"),context.runState().warnings());verify(opened).close();
    }
    @Test void approvalRejectsExtraFieldsDuplicatesAndOutOfOrderIdentity()throws Exception{
        var path=approval("[{\"id\":\"2\",\"sn\":null,\"specModel\":null,\"contract\":null,\"contractType\":null,\"item\":null,\"status\":null},{\"id\":\"1\",\"sn\":null,\"specModel\":null,\"contract\":null,\"contractType\":null,\"item\":null,\"status\":null}]");
        try(var cursor=new P37LicenseHistorySyncTask.ApprovalCursor(path)){assertEquals("2",cursor.next().id());assertThrows(IllegalStateException.class,cursor::next);}
        var extra=approval("[{\"id\":\"1\",\"sn\":null,\"specModel\":null,\"contract\":null,\"contractType\":null,\"item\":null,\"status\":null,\"unapproved\":true}]");
        try(var cursor=new P37LicenseHistorySyncTask.ApprovalCursor(extra)){assertThrows(IllegalStateException.class,cursor::next);}
    }
    @Test void currentApprovedScopeRequiresAll493037RowsAndRejectsExtraSourceRows()throws Exception{
        var path=directory.resolve("current-scope.json.gz");
        try(var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(path)),StandardCharsets.UTF_8))){
            writer.write("[");
            for(int id=1;id<=493037;id++){
                if(id>1)writer.write(",");
                writer.write("{\"id\":\""+id+"\",\"sn\":null,\"specModel\":null,\"contract\":null,\"contractType\":null,\"item\":null,\"status\":null}");
            }
            writer.write("]");
        }
        try(var cursor=new P37LicenseHistorySyncTask.ApprovalCursor(path)){
            for(int id=1;id<=493037;id++)assertEquals(Integer.toString(id),cursor.next().id());
            assertDoesNotThrow(cursor::finish);
        }
        try(var cursor=new P37LicenseHistorySyncTask.ApprovalCursor(path)){
            for(int id=1;id<=493036;id++)cursor.next();
            assertThrows(IllegalStateException.class,cursor::finish);
            assertEquals("493037",cursor.next().id());
            assertThrows(IllegalStateException.class,cursor::next);
        }
    }
    @Test void deviceReferenceStageStillChecksApprovedFactsWithoutReadingTheLicenseCode()throws Exception{
        var path=approval("[{\"id\":\"1\",\"sn\":\"SN-1\",\"specModel\":null,\"contract\":\"C-1\",\"contractType\":null,\"item\":\"ITEM\",\"status\":\"ACTIVE\"}]");
        var task=task(path);var source=mock(SyncSource.class);var opened=mock(SyncSource.Session.class);var nativeSession=mock(SqlSession.class);
        when(source.open(3)).thenReturn(opened);when(opened.sqlSession()).thenReturn(nativeSession);
        var row=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(1L).setSn("SN-1").setContract("C-1").setItem("ITEM").setStatus("ACTIVE");
        doAnswer(call->{ResultHandler<P37LicenseHistorySyncTask.LicenseSource> handler=call.getArgument(2);var result=mock(ResultContext.class);when(result.getResultObject()).thenReturn(row);handler.handleResult(result);return null;}).when(nativeSession).select(contains("selectDeviceReference"),any(),any(ResultHandler.class));
        var runtime=new SyncContext<Void>(1,2,3,true,500,1000000,LocalDateTime.now(),source,mock(SyncIdentityStore.class),mock(SyncReferences.class),"DeviceReference",mock(SqlSession.class),false,true);
        var context=runtime.forParameters(task.createParameters(runtime));int[] called={0};
        assertThrows(IllegalStateException.class,()->task.syncDataQueryByRowHandler(context,result->{assertNull(result.getResultObject().getLicenseCode());assertNull(result.getResultObject().getEnvelope());called[0]++;}));
        assertEquals(1,called[0]);verify(opened).close();
    }
    @Test void plaintextPreservesEmptyUnicodeAndRejectsNullOverlongOrMixedStorage(){
        var task=new P37LicenseHistorySyncTask(mock(P37LicenseHistorySyncTask.TargetMapper.class),"","TEST",directory.toString());
        var row=new P37LicenseHistorySyncTask.LicenseTarget().setTenantId(1L).setLicenseCode("");
        String empty=task.storedFingerprint(new P37LicenseHistorySyncTask.StoredContent(row,"7"));
        row.setLicenseCode("许可🙂");
        assertNotEquals(empty,task.storedFingerprint(new P37LicenseHistorySyncTask.StoredContent(row,"7")));
        assertNotEquals(P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(1,"7","许可🙂")),P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(2,"7","许可🙂")));
        assertThrows(IllegalArgumentException.class,()->P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(1,"7",null)));
        assertThrows(IllegalArgumentException.class,()->P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(1,"7","a".repeat(257))));
        row.setLicenseCodeCiphertext("legacy");
        assertThrows(IllegalStateException.class,()->task.storedFingerprint(new P37LicenseHistorySyncTask.StoredContent(row,"7")));
    }
    @Test void plaintextReplayKeepsHistoryAndRejectsChangedContent(){
        var mapper=mock(P37LicenseHistorySyncTask.TargetMapper.class);var task=new P37LicenseHistorySyncTask(mapper,"","TEST",directory.toString());
        var identities=mock(SyncIdentityStore.class);when(identities.findByKeys(any())).thenReturn(Map.of());
        var runtime=new SyncContext<Void>(1,2,3,false,500,1000000,LocalDateTime.now(),mock(SyncSource.class),identities,mock(SyncReferences.class),"LicenseHistory",mock(SqlSession.class),false,true);
        var context=runtime.forParameters(task.createParameters(runtime));
        var source=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(7L).setSn("SN-1").setCapturedLicenseCode("original")
                .setCodeFingerprint(P37LicenseHistorySyncTask.plainFingerprint(new P37LicenseHistorySyncTask.PlainContent(1,"7","original")));
        task.syncDataInsertBefore(new SyncBatch<>(context,List.of(source)));var old=source.getTarget();
        String fingerprint=P37LicenseHistorySyncTask.fingerprint(source,old);
        when(identities.findByKeys(any())).thenReturn(Map.of("7",new SyncIdentityStore.Identity("7",old.getId(),fingerprint)));
        when(mapper.selectExisting(any())).thenReturn(List.of(old));source.setCapturedLicenseCode("original");
        task.syncDataInsertBefore(new SyncBatch<>(context,List.of(source)));assertEquals(SyncWriteAction.UNCHANGED,source.getSyncAction());
        old.setLicenseCode("tampered");source.setCapturedLicenseCode("original");
        assertThrows(IllegalStateException.class,()->task.syncDataInsertBefore(new SyncBatch<>(context,List.of(source))));
    }
}
