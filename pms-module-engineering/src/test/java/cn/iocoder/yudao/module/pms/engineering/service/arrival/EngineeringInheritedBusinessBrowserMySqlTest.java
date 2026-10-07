package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.business.RequirementRevisionBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.business.RequirementRevisionBusinessService;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.business.RequirementRevisionBusinessController;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.business.SiteSurveyBusinessController;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringOperationResultSource;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.entity.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.business.ProjectBusinessDeliveryController;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice;
import cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real stored entities, current/revision transitions, child tables, files, materials and actual default SFCs.
 * Auth/project/technical storage and legacy layout/legacy file-slot ports remain deterministic fixtures. */
@EnabledIfSystemProperty(named="native.delivery.mysql",matches="true")
class EngineeringInheritedBusinessBrowserMySqlTest extends SurveyInheritedDeliveryMySqlTest {
    @Override List<Class<?>> extraMapperTypes(){var types=new ArrayList<>(super.extraMapperTypes());types.addAll(List.of(RequirementAnalysisMapper.class,RequirementRevisionBusinessMapper.class));return types;}
    @Override List<Class<?>> extraSchemaTypes(){var types=new ArrayList<>(super.extraSchemaTypes());types.addAll(List.of(RequirementAnalysisDO.class,RequirementAnalysisRevisionDO.class));return types;}
    @Override List<String> extraMapperPaths(){var paths=new ArrayList<>(super.extraMapperPaths());paths.add("requirement/RequirementAnalysisMapper.xml");return paths;}
    @Override void registerExtraBeans(SqlSessionTemplate sessions) {
        super.registerExtraBeans(sessions);
        context.removeBeanDefinition(EntityExtensionApi.class.getName());
        context.register(EntityExtensionService.class,cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityProvider.class);
        context.registerBean(ProjectParticipantFactApi.class,()->{
            var participants=mock(ProjectParticipantFactApi.class);var fact=new ProjectParticipantFact(20L,17L,Set.of("PROJECT_MANAGER"),"PRIMARY","ACTIVE","S1",0L,3L);
            when(participants.inspect(any())).thenReturn(fact);when(participants.lockAndRevalidate(any())).thenReturn(fact);return participants;
        });
        context.registerBean(ProjectWorkBindingFactApi.class,()->mock(ProjectWorkBindingFactApi.class));
        context.registerBean(ProjectNodeExecutionApi.class,()->mock(ProjectNodeExecutionApi.class));
        context.registerBean(InheritedRevisionAdapterFactory.class,()->mock(InheritedRevisionAdapterFactory.class));
        context.registerBean(RequirementAnalysisRevisionFiles.class,()->mock(RequirementAnalysisRevisionFiles.class));
        context.register(RequirementAnalysisAccess.class,RequirementAnalysisExecutionAccess.class,RequirementAnalysisEntityProvider.class,
                RequirementRevisionBusinessService.class,RequirementRevisionBusinessController.class,EntityProviderRegistry.class,EntityVersionService.class,
                EngineeringOperationResultSource.class,ExistingFileVersionAttachmentService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory.class);
    }
    @Override void initializeExtraOwners(SqlSessionTemplate sessions) {
        super.initializeExtraOwners(sessions);
        var extensionApi=context.getBean(EntityExtensionApi.class);var actor=new EntityActor(7L,17L,"browser-forms");
        var surveyDefinition=extensionApi.publishDefinition(7L,"SOL","SITE_SURVEY",List.of(new EntityExtensionApi.Definition("extra_flag","工勘扩展标志",EntityField.Type.BOOLEAN,false,null,List.of())),actor).id();
        var requirementDefinition=extensionApi.publishDefinition(7L,"SOL","REQUIREMENT_ANALYSIS",List.of(new EntityExtensionApi.Definition("CUSTOM_FLAG","需求扩展标志",EntityField.Type.BOOLEAN,false,null,List.of())),actor).id();
        when(context.getBean(EntityFormApi.class).layout(any(),any())).thenAnswer(invocation->{
            EntityDataRef ref=invocation.getArgument(0);boolean survey=ref.entity().entityType().equals("SITE_SURVEY");
            String fixed=survey?"name":"projectBackground",title=survey?"工勘名称":"项目背景",extra=survey?"extra_flag":"CUSTOM_FLAG",extraTitle=survey?"工勘扩展标志":"需求扩展标志";
            return new EntityFormApi.Layout(new EntityFormApi.Binding(1L,survey?surveyDefinition:requirementDefinition,Map.of(fixed,fixed,extra,extra),0),1L,1,1,"FORM_CREATE","1","1","{}",
                    "[{\"type\":\"input\",\"field\":\""+fixed+"\",\"title\":\""+title+"\"},{\"type\":\"switch\",\"field\":\""+extra+"\",\"title\":\""+extraTitle+"\"}]",List.of());
        });
        var businessForms=context.getBean(cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi.class);
        when(businessForms.inspectRevisionForUsage(any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact(7L,null,1L,1L,1,1,"SITE_SURVEY",null,"FORM_CREATE","1","1","{}","[]",List.of(),null));
        when(businessForms.validateRevisionValues(any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormValidationFact("VALID",List.of()));
        org.springframework.test.util.ReflectionTestUtils.setField(context.getBean(FileArtifactApi.class),"attachmentService",context.getBean(ExistingFileVersionAttachmentService.class));
        jdbc.execute("CREATE UNIQUE INDEX uk_ra_revision ON sol_requirement_analysis_revision(tenant_id,entity_id,revision_no)");
        jdbc.execute("CREATE UNIQUE INDEX uk_ra_project_revision ON sol_requirement_analysis_revision(tenant_id,project_id,revision_no)");
        jdbc.execute("CREATE UNIQUE INDEX uk_ra_draft ON sol_requirement_analysis_revision(tenant_id,project_id,draft_marker)");
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("../sql/migrations/V399__requirement_revision_active_identity_uniqueness.sql"));
        }catch(java.sql.SQLException error){throw new IllegalStateException(error);}
    }
    @Test @EnabledIfSystemProperty(named="native.delivery.browser",matches="true")
    void browserInheritedSurveyAndRequirementWithUnifiedFiles() throws Exception {
        try(var secured=new AnnotationConfigApplicationContext()) {
            secured.setParent(context);secured.register(SecuredControllers.class,SiteSurveyBusinessController.class,RequirementRevisionBusinessController.class,ProjectBusinessDeliveryController.class);secured.refresh();
            var json=tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
            var mvc=MockMvcBuilders.standaloneSetup(secured.getBean(SiteSurveyBusinessController.class),secured.getBean(RequirementRevisionBusinessController.class),secured.getBean(ProjectBusinessDeliveryController.class))
                    .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                    .setControllerAdvice(new BusinessModelContractAdvice(),new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("inherited-engineering",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",27462),0);
            server.createContext("/",exchange->{
                try {
                    login(7L);byte[] content;int status=200;
                    if(exchange.getRequestURI().getPath().equals("/fixture/evidence")) {
                        var evidence=new LinkedHashMap<String,Object>();
                        for(String table:List.of("sol_site_survey","sol_site_survey_material","sol_site_survey_condition","sol_requirement_analysis","sol_requirement_analysis_revision","plt_entity_extension_value","plt_delivery_material"))evidence.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));
                        evidence.put("storedObjects",stored.size());evidence.put("fileVersions",jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));content=json.writeValueAsBytes(evidence);
                    }else {
                        var request=browserRequest(exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Content-Type"),exchange.getRequestBody().readAllBytes());
                        exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                        var response=mvc.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                    }
                    exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
                }catch(Exception failure){failure.printStackTrace();byte[] content=json.writeValueAsBytes(Map.of("code",500,"msg",Objects.toString(failure.getMessage(),failure.getClass().getSimpleName())));exchange.sendResponseHeaders(500,content.length);exchange.getResponseBody().write(content);}
                finally{exchange.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            });server.start();
            try {
                var repo=Path.of("").toAbsolutePath();while(repo!=null&&!Files.isDirectory(repo.resolve("scripts/tests")))repo=repo.getParent();
                var output=repo.resolve(".run/engineering-inherited-browser/process.log");Files.createDirectories(output.getParent());
                var process=new ProcessBuilder("python3",repo.resolve("scripts/tests/run_engineering_inherited_browser.py").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if(!process.waitFor(240,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Browser timeout");}
                System.out.println(Files.readString(output));assertEquals(0,process.exitValue(),"See inherited engineering browser evidence");
            }finally{server.stop(0);}
        }
    }
}
