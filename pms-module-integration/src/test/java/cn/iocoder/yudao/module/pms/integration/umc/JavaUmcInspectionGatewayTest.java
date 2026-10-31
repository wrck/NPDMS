package cn.iocoder.yudao.module.pms.integration.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaUmcInspectionGatewayTest {
    static final String WORD="<?xml version=\"1.0\"?><w:wordDocument xmlns:w=\"http://schemas.microsoft.com/office/word/2003/wordml\"><w:body/></w:wordDocument>";
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    static UmcInspectionProperties settings(){var p=new UmcInspectionProperties();p.setBaseUrl("https://umc.test/");p.setUsername("fixture");p.setPassword("synthetic-only");p.setTimeoutSeconds(2);return p;}
    static UmcInspectionApi.Request input(UmcInspectionApi.Checkpoint cp,boolean query){return new UmcInspectionApi.Request(new UmcInspectionApi.Scope(1L,"task","attempt"),"request",List.of(new UmcInspectionApi.OfflineLog("Device(192.0.2.1)","synthetic log".getBytes(StandardCharsets.UTF_8))),cp,query);}
    static final class Fixture implements JavaUmcInspectionGateway.Transport {
        Map<String,Object> task;
        int created,uploaded,logout,queries;
        boolean uploadFailure,createFailure,queryFailure,wrongDevice,invalidDoc,authFailure,neverReady,logoutFailure,duplicateTask,conflict,duplicateReport,mismatchReport;
        String postedLogin;
        public JavaUmcInspectionGateway.Reply exchange(JavaUmcInspectionGateway.Call call) {
            String path=call.route().split("\\?")[0];Object body="";int status=200;
            switch(path){
                case "/UMC/code.jsp" -> body="{\"c\":1}";
                case "/UMC/Login!start.action" -> body="getElementById(\"c\").value=\"1\";cn=\"2\";cp=\"3\";";
                case "/UMC/Login.action" -> {postedLogin=new String(call.body(),StandardCharsets.UTF_8);assertFalse(postedLogin.contains("synthetic-only"));}
                case "/UMC/Logout.action" -> {logout++;if(logoutFailure)throw new IllegalStateException("secret must never escape");}
                case "/umc-api/dev/inspection/tasklist" -> {
                    if(authFailure){status=401;body="sensitive server content";}
                    else {var rows=task==null?List.of():duplicateTask?List.of(task,task):List.of(task);body=Map.of("code",200,"data",Map.of("total",rows.size(),"list",rows));}
                }
                case "/umc-api/dev/inspection/taskadd" -> {
                    assertTrue(new String(call.body(),StandardCharsets.UTF_8).contains("\"id\":null"));
                    created++;task=JavaUmcInspectionGateway.object(JavaUmcInspectionGateway.parse(call.body()));task.put("id","9007199254740993");if(conflict)task.put("descr","another-input");if(createFailure)throw new IllegalStateException("unknown after task creation");
                }
                case "/umc-api/dev/inspection/offlinetaskstart" -> {uploaded++;assertTrue(new String(call.body(),StandardCharsets.UTF_8).contains("Device(192.0.2.1)"));if(uploadFailure)throw new IllegalStateException("unknown after upload");}
                case "/umc-api/dev/inspection/reportlist" -> {
                    queries++;if(queryFailure)throw new IllegalStateException("before upload");
                    var report=Map.of("id","9007199254740995","taskid","9007199254740993","doc",!neverReady);
                    var list=uploaded==0?List.of():duplicateReport?List.of(report,report):List.of(report);body=Map.of("code",200,"data",Map.of("total",list.size(),"list",list));
                }
                case "/umc-api/dev/inspection/report" -> body=Map.of("id",mismatchReport?"different":"9007199254740995","taskid","9007199254740993","devlist",List.of(Map.of("deviceip",wrongDevice?"192.0.2.2":"192.0.2.1","rulelist",List.of(Map.of("ruleid",9007199254740997L,"support",true,"result",false,"error","","targetlist",List.of())))));
                case "/umc-api/dev/inspection/report_download" -> body=invalidDoc?"<html>session expired</html>":WORD;
                default -> fail("Unexpected external request: "+path);
            }
            return new JavaUmcInspectionGateway.Reply(status,body instanceof String s?s.getBytes(StandardCharsets.UTF_8):JavaUmcInspectionGateway.jsonBytes(body));
        }
        public void close() { }
        JavaUmcInspectionGateway gateway(){return new JavaUmcInspectionGateway(settings(),p->this);}
    }
    @Test void uploadsOnceAndPreservesLongIdsAndOriginalEvidenceOnRecovery(){
        var f=new Fixture();var first=f.gateway().inspect(input(null,false));
        assertEquals("9007199254740993",first.checkpoint().taskId());assertEquals("9007199254740995",first.checkpoint().reportId());
        assertEquals(1,f.created);assertEquals(1,f.uploaded);assertEquals("ACCEPTED",first.checkpoint().uploadState());
        assertEquals("04bab24b2929b37e82fb9f05f9b30148b8bedad1f9508f5e6a5c2cfc370c82a6",first.checkpoint().inputDigest());
        assertEquals("NPDMS_d16bef0e82c0e755e9699fb02a40",f.task.get("name"));
        var rule=first.detail().devlist().getFirst().rulelist().getFirst();assertEquals("9007199254740997",rule.ruleid());assertTrue(rule.support());assertFalse(rule.result());assertEquals("",rule.error());assertTrue(rule.targetlist().isEmpty());
        var second=f.gateway().inspect(input(first.checkpoint(),false));assertArrayEquals(first.rawDetail(),second.rawDetail());assertArrayEquals(first.doc(),second.doc());assertEquals(1,f.created);assertEquals(1,f.uploaded);assertEquals(2,f.logout);
        byte[] doc=first.doc();doc[0]=0;assertNotEquals(0,first.doc()[0]);
    }
    @Test void taskCreationUnknownRecoversByStableScopeAndDigestWithoutAnotherCreate(){var f=new Fixture();f.createFailure=true;var failure=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals("TASK_CREATION_OUTCOME_UNKNOWN",failure.code());f.createFailure=false;f.gateway().inspect(input(failure.checkpoint(),false));assertEquals(1,f.created);assertEquals(1,f.uploaded);}
    @Test void lostUploadResponseRetainsUnknownAndNeverUploadsAgain(){var f=new Fixture();f.uploadFailure=true;var failure=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals("UNKNOWN",failure.checkpoint().uploadState());f.uploadFailure=false;f.gateway().inspect(input(failure.checkpoint(),false));assertEquals(1,f.uploaded);}
    @Test void unknownWithoutReportTimesOutWithoutAutomaticReupload(){var f=new Fixture();f.uploadFailure=true;var first=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));f.uploaded=0;f.uploadFailure=false;var retry=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(first.checkpoint(),false)));assertEquals("UPLOAD_OUTCOME_UNKNOWN",retry.code());assertEquals("UNKNOWN",retry.checkpoint().uploadState());assertEquals(0,f.uploaded);}
    @Test void queryOnlyExistingTaskDoesNotUploadEvenWithEarlierNotSentCheckpoint(){var f=new Fixture();f.queryFailure=true;var first=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));f.queryFailure=false;var retry=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(first.checkpoint(),true)));assertEquals("UPLOAD_OUTCOME_UNKNOWN",retry.code());assertEquals(0,f.uploaded);assertEquals(1,f.created);}
    @Test void queryOnlyNeverCreatesMissingTask(){var f=new Fixture();assertEquals("RECOVERY_TASK_NOT_FOUND",assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,true))).code());assertEquals(0,f.created);assertEquals(0,f.uploaded);}
    @Test void failureBeforeUploadRetainsKnownNotSentAndExistingTask(){var f=new Fixture();f.queryFailure=true;var e=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals("NOT_SENT",e.checkpoint().uploadState());assertNotNull(e.checkpoint().taskId());assertEquals(0,f.uploaded);f.queryFailure=false;f.gateway().inspect(input(e.checkpoint(),false));assertEquals(1,f.created);}
    @Test void changedInputCheckpointCannotPerformNetwork(){var f=new Fixture();var first=f.gateway().inspect(input(null,false));var bad=new UmcInspectionApi.Checkpoint("other",first.checkpoint().taskId(),null,"NOT_SENT");assertEquals("CHECKPOINT_MISMATCH",assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(bad,false))).code());assertEquals(1,f.created);}
    @Test void mismatchedTaskAndReportCheckpointsFailWithoutResubmission(){var f=new Fixture();var result=f.gateway().inspect(input(null,false));var cp=result.checkpoint();for(var bad:List.of(new UmcInspectionApi.Checkpoint(cp.inputDigest(),"other",null,"ACCEPTED"),new UmcInspectionApi.Checkpoint(cp.inputDigest(),cp.taskId(),"other","ACCEPTED")))assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(bad,false)));assertEquals(1,f.uploaded);}
    @Test void wrongDeviceAndFakeDocAreRejectedAfterCheckpoint(){for(boolean wrong:List.of(true,false)){var f=new Fixture();f.wrongDevice=wrong;f.invalidDoc=!wrong;var e=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals(wrong?"DETAIL_DEVICE_MISMATCH":"INVALID_DOC",e.code());assertNotNull(e.checkpoint().reportId());assertEquals(1,f.logout);}}
    @Test void ambiguousTasksAndConflictingInputsCannotUpload(){var f=new Fixture();f.conflict=true;assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals(0,f.uploaded);var duplicate=new Fixture();duplicate.gateway().inspect(input(null,false));duplicate.duplicateTask=true;assertEquals("AMBIGUOUS_TASK",assertThrows(UmcInspectionApi.Failure.class,()->duplicate.gateway().inspect(input(null,false))).code());assertEquals(1,duplicate.uploaded);}
    @Test void duplicateReportsAndDetailMismatchAreRejected(){for(boolean duplicate:List.of(true,false)){var f=new Fixture();f.duplicateReport=duplicate;f.mismatchReport=!duplicate;assertEquals(duplicate?"AMBIGUOUS_REPORT":"DETAIL_IDENTITY_MISMATCH",assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false))).code());}}
    @Test void loginFailureAndLogoutWarningDoNotLeakCredentials(){var f=new Fixture();f.authFailure=true;var e=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals("LOGIN_FAILED",e.code());assertFalse(e.toString().contains("sensitive"));assertEquals(1,f.logout);var logout=new Fixture();logout.logoutFailure=true;assertEquals(List.of("LOGOUT_UNCONFIRMED"),logout.gateway().inspect(input(null,false)).warnings());}
    @Test void timeoutKeepsAcceptedCheckpointAndAttemptsLogout(){var f=new Fixture();f.neverReady=true;var e=assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false)));assertEquals("TIMEOUT",e.code());assertEquals("ACCEPTED",e.checkpoint().uploadState());assertEquals(1,f.logout);}
    @Test void tenantAndMissingConfigFailBeforeNetwork(){var f=new Fixture();TenantContextHolder.setTenantId(2L);assertEquals("INVALID_INPUT",assertThrows(UmcInspectionApi.Failure.class,()->f.gateway().inspect(input(null,false))).code());TenantContextHolder.setTenantId(1L);assertEquals("NOT_CONFIGURED",assertThrows(UmcInspectionApi.Failure.class,()->new JavaUmcInspectionGateway(new UmcInspectionProperties(),p->{fail("network");return f;}).inspect(input(null,false))).code());}
    @Test void filenamesAndDuplicateIpAreValidatedWithoutDnsOrNetwork(){for(String s:List.of("../192.0.2.1","Device(192.0.2.999)","host","001.2.3.4","x\r\n(192.0.2.1)"))assertThrows(UmcInspectionApi.Failure.class,()->JavaUmcInspectionGateway.fileIp(s));assertEquals("192.0.2.1",JavaUmcInspectionGateway.fileIp("交换机（192.0.2.1）"));var r=input(null,false);var duplicate=new UmcInspectionApi.Request(r.scope(),r.requestId(),List.of(r.logs().getFirst(),r.logs().getFirst()),null);assertEquals("DUPLICATE_DEVICE_IP",assertThrows(UmcInspectionApi.Failure.class,()->new Fixture().gateway().inspect(duplicate)).code());}
    @Test void wordMlRejectsMalformedHtmlAndExternalEntities()throws Exception {JavaUmcInspectionGateway.validateWordXml(WORD.getBytes(StandardCharsets.UTF_8));for(String text:List.of("<html/>",WORD.replace("</w:wordDocument>",""),WORD.replace("<w:wordDocument","<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///not-read'>]><w:wordDocument")))assertThrows(Exception.class,()->JavaUmcInspectionGateway.validateWordXml(text.getBytes(StandardCharsets.UTF_8)));}
}
