package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.briefing.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.*;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Two actual native Owners on a separate exclusive database. No role/login/storage or migration acceptance. */
@EnabledIfSystemProperty(named="native.delivery.mysql",matches="true")
class NativeTwoDeliveryMySqlTest extends NativeAttachmentDeliveryMySqlTest {
 enum Kind {
  BRIEFING("SOL","briefing","BRIEFING_ATTACHMENT","SOL.BRIEFING_ATTACHMENT","sol_eng_briefing",51L),
  LEGACY_ACCEPTANCE("ACC","acceptance","LEGACY_ACCEPTANCE_ATTACHMENT","ACC.LEGACY_ACCEPTANCE_ATTACHMENT","acc_acceptance_record",52L);
  final String module,type,purpose,source,table;final Long id;
  Kind(String m,String t,String p,String s,String table,Long id){module=m;type=t;purpose=p;source=s;this.table=table;this.id=id;}
  Long project(){return 20L;}
 }
 @Override String testJdbcUrl(){return "jdbc:mysql://127.0.0.1:28501/native_delivery_verify?useSSL=false&allowPublicKeyRetrieval=true";}
 @Override List<Class<?>> extraMapperTypes(){return List.of(BriefingMapper.class,AcceptanceMapper.class);}
 @Override List<Class<?>> extraSchemaTypes(){return List.of(BriefingDO.class,AcceptanceDO.class);}
 @Override List<String> extraMapperPaths(){return List.of("briefing/BriefingMapper.xml","acceptance/AcceptanceMapper.xml");}
 @Override List<BusinessModelDeclaration> extraDeclarations(SqlSessionTemplate sessions){return List.of(
  declaration(Kind.BRIEFING,BriefingDO.class,sessions.getMapper(BriefingMapper.class),"pms:sol-briefing:query"),
  declaration(Kind.LEGACY_ACCEPTANCE,AcceptanceDO.class,sessions.getMapper(AcceptanceMapper.class),"pms:acc-acceptance:query"));}
 BusinessModelDeclaration declaration(Kind k,Class<?> type,com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper,String permission){
  return new BusinessModelDeclaration(new BusinessModelDescriptor(k.module,k.type,"FIXTURE_"+k.name(),1,BusinessModelKind.AGGREGATE_ROOT,"隔离原生附件",permission,
   k.project()==null?List.of():List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),k.table),type,mapper,null);
 }
 @Override void registerExtraBeans(SqlSessionTemplate sessions){
  context.register(SupplementalAttachmentAccess.class,SupplementalAttachmentPolicies.class,SupplementalAttachmentSources.class,
    SupplementalAttachmentRegistration.class,BriefingAttachmentDeliveryAccess.class,
    BriefingServiceImpl.class,BriefingGeneratedFilePolicy.class,
    LegacyAcceptanceAttachmentFilePolicy.class,LegacyAcceptanceAttachmentSources.class,LegacyAcceptanceAttachmentRegistration.class,
    LegacyAcceptanceAttachmentDeliveryAccess.class,AcceptanceServiceImpl.class,NativeGeneratedFileService.class);
  context.getBeanFactory().registerSingleton("nativeFileAccessTickets",mock(FileAccessTicketService.class));
 }
 @Override void initializeExtraOwners(SqlSessionTemplate sessions){
  var briefing=new BriefingDO();briefing.setId(51L);briefing.setTenantId(7L);briefing.setProjectId(20L);briefing.setCode("BR-51");briefing.setName("隔离交底");briefing.setBriefingType("STANDARD");briefing.setContent("真实人工交底正文");briefing.setSourceSnapshot("真实来源基线-v1");briefing.setStatus(0);briefing.setVersion(0L);briefing.setRemark("Persisted native Owner");sessions.getMapper(BriefingMapper.class).insert(briefing);
  var acceptance=new AcceptanceDO();acceptance.setId(52L);acceptance.setTenantId(7L);acceptance.setProjectId(20L);acceptance.setCode("ACT-52");acceptance.setName("隔离历史验收");acceptance.setAcceptanceType("PRELIMINARY");acceptance.setStatus(0);acceptance.setVersion(0L);acceptance.setRemark("Persisted native Owner");sessions.getMapper(AcceptanceMapper.class).insert(acceptance);
 }
 FileUploadCompleted twoUpload(Kind k,String slot){
  byte[] bytes=("Actual "+k.name()+" attachment\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);var uploads=context.getBean(FileUploadApplicationService.class);
  var init=uploads.initialize(new FileUploadInitializeCommand(7L,17L,slot+":init","CREATE_ARTIFACT",null,null,k.module,k.type,k.id.toString(),k.purpose,slot,"native.txt",k.purpose,(long)bytes.length,"text/plain",null));
  return uploads.complete(new FileUploadCompleteCommand(7L,17L,slot+":complete",init.artifactId(),init.sessionId(),new MockMultipartFile("file","native.txt","text/plain",bytes),null));
 }
 void twoSave(Kind k,String remark){switch(k){
  case BRIEFING->{var r=BeanUtils.toBean(context.getBean(BriefingMapper.class).selectById(k.id),BriefingSaveReqVO.class);r.setRemark(remark);context.getBean(BriefingServiceImpl.class).updateBriefing(r);}
  case LEGACY_ACCEPTANCE->{var r=BeanUtils.toBean(context.getBean(AcceptanceMapper.class).selectById(k.id),AcceptanceSaveReqVO.class);r.setRemark(remark);context.getBean(AcceptanceServiceImpl.class).updateAcceptance(r);}
 }}
 void twoFreeze(Kind k){switch(k){
  case BRIEFING->{var request=new BriefingGenerateReqVO();request.setId(k.id);request.setVersion(context.getBean(BriefingMapper.class).selectById(k.id).getVersion().intValue());context.getBean(BriefingServiceImpl.class).generateBriefing(request);}
  case LEGACY_ACCEPTANCE->context.getBean(AcceptanceServiceImpl.class).submitAcceptance(k.id);
 }}
 List<PlatformDeliveryMaterialApi.DeliveryMaterialView> twoMaterials(Kind k){return context.getBean(PlatformDeliveryMaterialApi.class).listByEntity(k.module,k.type,k.id);}
 void twoRequirement(Kind k){
  context.getBean(DeliveryCatalogService.class).createType(k.source,"隔离原生附件",k.purpose,List.of("txt"),5242880L,"exclusive test fixture; no production seed");
  if(k==Kind.BRIEFING)context.getBean(DeliveryCatalogService.class).createType("BRIEFING_DOCUMENT","生成交底", "BRIEFING_DOCUMENT",List.of("html","pdf"),52428800L,"exclusive fixture");
  var r=new DeliveryRequirementDO();r.setOwnerModule(k.module);r.setEntityType(k.type);r.setEntityId(k.id);r.setProjectId(k.project());r.setTypeCode(k.source);r.setRequirementKind("CATALOG");r.setRequired(true);r.setMinimumQuantity(1);r.setCountingUnit("MATERIAL");r.setStatus("OPEN");context.getBean(DeliveryRequirementMapper.class).insert(r);
 }
 boolean twoComplete(Kind k){return context.getBean(DeliveryRequirementService.class).evaluateCompletion(jdbc.queryForObject("SELECT id FROM plt_delivery_requirement WHERE owner_module=? AND entity_type=? AND entity_id=?",Long.class,k.module,k.type,k.id)).satisfied();}
 long twoVersion(Kind k){return jdbc.queryForObject("SELECT version FROM "+k.table+" WHERE id=?",Long.class,k.id);}
 @ParameterizedTest @EnumSource(Kind.class)
 void twoActualSaveSharedMaterialCompletionAndWithdrawal(Kind k){
  twoRequirement(k);var file=twoUpload(k,"actual");assertTrue(twoMaterials(k).isEmpty());twoSave(k,"actual save");var m=twoMaterials(k).getFirst();
  assertEquals(k.source,m.typeCode());assertEquals(file.artifactId(),m.fileArtifactId());assertEquals(k.project(),m.projectId());assertNull(context.getBean(DeliveryMaterialMapper.class).selectById(m.id()).getSourceRevisionId());assertNull(m.requirementId());assertTrue(twoComplete(k));
  twoSave(k,"idempotent second save");assertEquals(1,twoMaterials(k).size());assertEquals(m.id(),twoMaterials(k).getFirst().id());
  assertEquals(k.project()!=null,context.getBean(PlatformDeliveryMaterialApi.class).listByProject(20L).stream().anyMatch(x->x.id().equals(m.id())));
  context.getBean(DeliveryMaterialService.class).withdraw(m.id());assertFalse(twoComplete(k));assertEquals("WITHDRAWN",twoMaterials(k).getFirst().status());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_submission",Integer.class));
 }
 @ParameterizedTest @EnumSource(Kind.class)
 void twoRegistrationFailureRollsBackActualOwnerAndRetriesFile(Kind k){
  twoUpload(k,"retry");jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_native CHECK(source_entity_id<>"+k.id+")");
  assertThrows(RuntimeException.class,()->twoSave(k,"must rollback"));assertTrue(twoMaterials(k).isEmpty());assertEquals(0L,twoVersion(k));assertEquals("Persisted native Owner",jdbc.queryForObject("SELECT remark FROM "+k.table+" WHERE id=?",String.class,k.id));
  jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_native");twoSave(k,"retry saved");assertEquals(1,twoMaterials(k).size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
 }
 @ParameterizedTest @EnumSource(Kind.class)
 void twoNativeFreezeCollectsUsingActualActionPermissionAndPreservesRead(Kind k){
  twoRequirement(k);twoUpload(k,"freeze");String permission=switch(k){case BRIEFING->"pms:sol-briefing:update";case LEGACY_ACCEPTANCE->"pms:acc-acceptance:update";};
  when(permissions.hasAnyPermissions(17L,permission)).thenReturn(false);twoFreeze(k);var materials=twoMaterials(k);assertFalse(materials.isEmpty());assertTrue(twoComplete(k));
  assertThrows(RuntimeException.class,()->twoUpload(k,"late"));assertThrows(RuntimeException.class,()->context.getBean(DeliveryMaterialService.class).withdraw(materials.getFirst().id()));assertFalse(context.getBean(FileArtifactApi.class).inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(new FileReferenceSetKey(k.module,k.type,k.id.toString(),k.purpose)),FileActionCodes.READ)).isEmpty());
 }
 @ParameterizedTest @EnumSource(Kind.class)
 void twoTenantAndPrecisePermissionAreRequired(Kind k){
  String p=switch(k){case BRIEFING->"pms:sol-briefing:update";case LEGACY_ACCEPTANCE->"pms:acc-acceptance:update";};
  when(permissions.hasAnyPermissions(17L,p)).thenReturn(false);assertThrows(RuntimeException.class,()->twoUpload(k,"query-only"));when(permissions.hasAnyPermissions(17L,p)).thenReturn(true);
  login(8L);assertThrows(RuntimeException.class,()->context.getBean(FileArtifactApi.class).inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(new FileReferenceSetKey(k.module,k.type,k.id.toString(),k.purpose)),FileActionCodes.READ)));login(7L);
 }
 @Test void twoBriefingManualAndGeneratedSnapshotsKeepDifferentPurposesAndActualRoot(){
  twoRequirement(Kind.BRIEFING);var manual=twoUpload(Kind.BRIEFING,"manual");twoFreeze(Kind.BRIEFING);var materials=twoMaterials(Kind.BRIEFING);assertEquals(2,materials.size());
  var generated=materials.stream().filter(m->m.typeCode().equals("BRIEFING_DOCUMENT")).findFirst().orElseThrow();var attachment=materials.stream().filter(m->m.typeCode().equals(Kind.BRIEFING.source)).findFirst().orElseThrow();
  assertEquals(manual.artifactId(),attachment.fileArtifactId());assertNotEquals(attachment.fileArtifactId(),generated.fileArtifactId());var source=context.getBean(DeliveryMaterialMapper.class).selectById(generated.id());assertEquals("SOL",source.getSourceOwnerModule());assertEquals("briefing",source.getSourceEntityType());assertEquals(51L,source.getSourceEntityId());assertNull(source.getSourceRevisionId());
  var row=context.getBean(BriefingMapper.class).selectById(51L);assertEquals("/api/v1/pms/briefings/51/files/"+generated.id(),row.getFileUrl());assertEquals("真实来源基线-v1",row.getSourceSnapshot());assertEquals("真实人工交底正文",row.getContent());
  assertTrue(stored.values().stream().map(b->new String(b,java.nio.charset.StandardCharsets.UTF_8)).anyMatch(t->t.contains("<!DOCTYPE html>")&&t.contains("真实人工交底正文")));
  assertThrows(RuntimeException.class,()->context.getBean(FileUploadApplicationService.class).initialize(new FileUploadInitializeCommand(7L,17L,"overwrite-generated","CREATE_ARTIFACT",null,null,"SOL","BRIEFING_DOCUMENT","51","BRIEFING_DOCUMENT_HTML/0","overwrite","fake.html","BRIEFING_DOCUMENT",4L,"text/html",null)));
  assertEquals(2,twoMaterials(Kind.BRIEFING).size());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
 }
 @Test void twoGenerationFailureRollsBackManualCollectionAndOwner(){
  twoRequirement(Kind.BRIEFING);twoUpload(Kind.BRIEFING,"pending");when(permissions.hasAnyPermissions(17L,"pms:file:upload")).thenReturn(false);
  assertThrows(RuntimeException.class,()->twoFreeze(Kind.BRIEFING));assertTrue(twoMaterials(Kind.BRIEFING).isEmpty());assertEquals(0L,twoVersion(Kind.BRIEFING));assertEquals(0,context.getBean(BriefingMapper.class).selectById(51L).getStatus());
  when(permissions.hasAnyPermissions(17L,"pms:file:upload")).thenReturn(true);twoFreeze(Kind.BRIEFING);assertEquals(2,twoMaterials(Kind.BRIEFING).size());
 }
    @Test @EnabledIfSystemProperty(named="native.delivery.browser",matches="true")
    void twoChromiumTwoPagesUploadCollectRetryWithdraw() throws Exception {
        for(var kind:Kind.values())twoRequirement(kind);
        try(var secured=new AnnotationConfigApplicationContext()){
            secured.setParent(context);secured.register(SecuredControllers.class,cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.BriefingController.class, cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.AcceptanceController.class, cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class, cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class);
            secured.refresh();
            var json=tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
            var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.BriefingController.class), secured.getBean(cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.AcceptanceController.class), secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class), secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class))
                .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice(),new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("native-two",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",28502),0);
            server.createContext("/",exchange->{
                try{
                    login(7L);String path=exchange.getRequestURI().getPath();byte[] content;int status=200;
                    if(path.startsWith("/fixture/")){
                        var parts=path.split("/");var kind=Kind.valueOf(parts[3]);
                        if("reject".equals(parts[2])){jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_native CHECK(source_entity_id<>"+kind.id+")");content="{}".getBytes();}
                        else if("allow".equals(parts[2])){jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_native");content="{}".getBytes();}
                        else{
                            var evidence=new LinkedHashMap<String,Object>();evidence.put("materials",twoMaterials(kind));
                            evidence.put("projectMaterials",context.getBean(PlatformDeliveryMaterialApi.class).listByProject(20L));
                            evidence.put("version",jdbc.queryForObject("SELECT version FROM "+kind.table+" WHERE id=?",Long.class,kind.id));
                            evidence.put("remark",jdbc.queryForObject("SELECT remark FROM "+kind.table+" WHERE id=?",String.class,kind.id));
                            evidence.put("files",jdbc.queryForObject("SELECT COUNT(DISTINCT a.id) FROM plt_file_artifact a JOIN plt_file_reference r ON r.artifact_id=a.id WHERE r.owner_context=? AND r.object_type=? AND r.object_id=?",Long.class,kind.module,kind.type,String.valueOf(kind.id)));
                            evidence.put("completed",twoComplete(kind));content=json.writeValueAsBytes(evidence);
                        }
                    }else{
                        var request=browserRequest(exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Content-Type"),exchange.getRequestBody().readAllBytes());
                        exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                        var response=http.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                    }
                    exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
                }catch(Exception error){error.printStackTrace();byte[] failure=json.writeValueAsBytes(Map.of("code",500,"msg",error.getClass().getSimpleName()));exchange.sendResponseHeaders(500,failure.length);exchange.getResponseBody().write(failure);}
                finally{exchange.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            });server.start();
            try{
                var repository=java.nio.file.Path.of("").toAbsolutePath();while(repository!=null&&!java.nio.file.Files.isDirectory(repository.resolve("scripts/tests")))repository=repository.getParent();
                var output=repository.resolve(".run/native-delivery-two-20261006/browser-process.log");
                var process=new ProcessBuilder("python3",repository.resolve("scripts/tests/run_native_two_delivery_browser.py").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Native browser timeout");}
                System.out.println(java.nio.file.Files.readString(output));assertEquals(0,process.exitValue(),"See two browser evidence");
            }finally{server.stop(0);}
        }
    }
 @Test void twoManualSaveCannotOverwriteGeneratedMetadataAfterRejection(){
  twoRequirement(Kind.BRIEFING);twoUpload(Kind.BRIEFING,"actual manual");twoFreeze(Kind.BRIEFING);var mapper=context.getBean(BriefingMapper.class);var before=mapper.selectById(51L);
  var reject=new BriefingApproveReqVO();reject.setId(51L);reject.setVersion(before.getVersion().intValue());reject.setApproveAction("REJECT");context.getBean(BriefingServiceImpl.class).approveBriefing(reject);
  var forged=BeanUtils.toBean(mapper.selectById(51L),BriefingSaveReqVO.class);forged.setFileChecksum("0".repeat(64));assertThrows(RuntimeException.class,()->context.getBean(BriefingServiceImpl.class).updateBriefing(forged));assertEquals(before.getFileChecksum(),mapper.selectById(51L).getFileChecksum());
  var request=BeanUtils.toBean(mapper.selectById(51L),BriefingSaveReqVO.class);request.setRemark("manual attachment save");context.getBean(BriefingServiceImpl.class).updateBriefing(request);var after=mapper.selectById(51L);
  assertEquals(before.getFileUrl(),after.getFileUrl());assertEquals(before.getFileName(),after.getFileName());assertEquals(before.getFileSize(),after.getFileSize());assertEquals(before.getFileChecksum(),after.getFileChecksum());assertEquals(before.getSourceSnapshot(),after.getSourceSnapshot());assertEquals(2,twoMaterials(Kind.BRIEFING).size());
 }
 @ParameterizedTest @EnumSource(Kind.class)
 void twoPreciseFreezePermissionFailureRollsBackMaterialAndOwner(Kind k){
  twoUpload(k,"pending final");String permission=switch(k){case BRIEFING->"pms:sol-briefing:generate";case LEGACY_ACCEPTANCE->"pms:acc-acceptance:submit";};
  when(permissions.hasAnyPermissions(17L,permission)).thenReturn(false);assertThrows(RuntimeException.class,()->twoFreeze(k));assertTrue(twoMaterials(k).isEmpty());assertEquals(0L,twoVersion(k));
 }
 @ParameterizedTest @EnumSource(value=Kind.class,names={"BRIEFING","LEGACY_ACCEPTANCE"})
 void twoViewOnlyProjectScopeCannotCollectOnNativeFreeze(Kind k){
  twoUpload(k,"pending view only");when(scopes.resolveCurrent(any())).thenAnswer(call->{var q=call.getArgument(0,ProjectCurrentScopeQuery.class);return ProjectScopeApi.ACTION_MANAGE.equals(q.actionCode())?new ProjectScopeResult(20L,3L,Set.of(),Set.of()):scope();});
  assertThrows(RuntimeException.class,()->twoFreeze(k));assertTrue(twoMaterials(k).isEmpty());assertEquals(0L,twoVersion(k));
 }
 @ParameterizedTest @EnumSource(Kind.class)
 void twoReplacementAndDetachInvalidateFrozenMaterialWithoutMakingHistory(Kind k){
  twoRequirement(k);var first=twoUpload(k,"replace-slot");twoSave(k,"first");byte[] bytes="Actual new version\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);var uploads=context.getBean(FileUploadApplicationService.class);
  var init=uploads.initialize(new FileUploadInitializeCommand(7L,17L,"replace:init","ADD_VERSION",first.artifactId(),0,k.module,k.type,k.id.toString(),k.purpose,"replace-slot","native.txt",k.purpose,(long)bytes.length,"text/plain",null));
  var replacement=uploads.complete(new FileUploadCompleteCommand(7L,17L,"replace:complete",init.artifactId(),init.sessionId(),new MockMultipartFile("file","native.txt","text/plain",bytes),null));twoSave(k,"replacement");assertEquals(2,twoMaterials(k).size());assertThrows(RuntimeException.class,()->twoComplete(k));
  var old=twoMaterials(k).stream().filter(m->m.fileVersionNo()==1).findFirst().orElseThrow();context.getBean(DeliveryMaterialService.class).withdraw(old.id());assertTrue(twoComplete(k));
  context.getBean(FileLifecycleApplicationService.class).detach(new DetachFileReferenceCommand(7L,17L,"detach",replacement.referenceId(),1,k.module,k.type,k.id.toString(),k.purpose,"replace-slot","actual correction"));assertThrows(RuntimeException.class,()->twoComplete(k));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_submission",Integer.class));
 }
 @Test void twoLegacyAcceptanceCodeAllocationStillIncludesSoftDeletedRows(){
  var mapper=context.getBean(AcceptanceMapper.class);assertTrue(mapper.selectRecordCodesIncludeDeleted(20L).contains("ACT-52"));mapper.deleteById(52L);assertTrue(mapper.selectRecordCodesIncludeDeleted(20L).contains("ACT-52"));
 }
 @ParameterizedTest @EnumSource(value=Kind.class,names={"BRIEFING"})
 void twoRawUrlCannotCreateOrUpdateAnUnregisteredAttachment(Kind k){switch(k){
  case BRIEFING->{var r=BeanUtils.toBean(context.getBean(BriefingMapper.class).selectById(k.id),BriefingSaveReqVO.class);r.setFileUrl("https://anonymous.example/file.txt");assertThrows(RuntimeException.class,()->context.getBean(BriefingServiceImpl.class).updateBriefing(r));r.setId(null);assertThrows(RuntimeException.class,()->context.getBean(BriefingServiceImpl.class).createBriefing(r));}
  case LEGACY_ACCEPTANCE->throw new AssertionError("Legacy DTO has no attachmentUrl field");
 }assertEquals(0L,twoVersion(k));assertTrue(twoMaterials(k).isEmpty());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));}
 @Test void twoAssemblyDoesNotRegisterKnoNativePolicyOrValidator(){
  assertTrue(context.getBeansOfType(FileBusinessObjectPolicyProvider.class).values().stream().noneMatch(policy->"KNO".equals(policy.ownerContext())));
  assertTrue(context.getBeansOfType(DeliveryMaterialUploadPolicyValidator.class).values().stream().noneMatch(policy->"KNO".equals(policy.ownerModule())));
 }
}
