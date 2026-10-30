package cn.iocoder.yudao.module.pms.project.controller.admin.projects;
import cn.iocoder.yudao.module.system.api.user.ProjectMemberRoleSelectionApi;import cn.iocoder.yudao.module.system.api.user.ProjectMemberIdentityScopeProvider;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMemberFilterPageReqVO;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockHttpServletRequest;import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ProjectMemberFilterControllerTest {
 @Test void onlyForwardsFieldKeywordAndPaginationToTrustedSystemApi(){var api=mock(ProjectMemberRoleSelectionApi.class);var controller=new ProjectSelectionController(mock(cn.iocoder.yudao.module.pms.project.service.projectselection.ProjectSelectionQueryService.class));ReflectionTestUtils.setField(controller,"projectMemberRoleSelectionApi",api);var request=new MockHttpServletRequest();request.addParameter("field","MANAGER");var query=new ProjectMemberFilterPageReqVO();query.setField(ProjectMemberIdentityScopeProvider.Filter.MANAGER);query.setKeyword("name");query.setPageNo(2);query.setPageSize(20);controller.memberFilterOptions(query,request);verify(api).page(new ProjectMemberRoleSelectionApi.Query(2,20,"name",cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.MANAGEMENT_CODES));}
 @Test void callerCannotSupplyIdsTenantActorOrPermissionScope(){var api=mock(ProjectMemberRoleSelectionApi.class);var controller=new ProjectSelectionController(mock(cn.iocoder.yudao.module.pms.project.service.projectselection.ProjectSelectionQueryService.class));ReflectionTestUtils.setField(controller,"projectMemberRoleSelectionApi",api);for(var field:java.util.List.of("userIds","tenantId","actorId","projectIds","readScope","roleCodes","roles")){var request=new MockHttpServletRequest();request.addParameter(field,"1");assertThrows(RuntimeException.class,()->controller.memberFilterOptions(new ProjectMemberFilterPageReqVO(),request));}verifyNoInteractions(api);}
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.EnumSource(ProjectMemberIdentityScopeProvider.Filter.class)
 void mapsEachFieldToTheExistingRoleSetIncludingServiceManagerAliases(ProjectMemberIdentityScopeProvider.Filter field){
  var api=mock(ProjectMemberRoleSelectionApi.class);
  var controller=new ProjectSelectionController(mock(cn.iocoder.yudao.module.pms.project.service.projectselection.ProjectSelectionQueryService.class));
  ReflectionTestUtils.setField(controller,"projectMemberRoleSelectionApi",api);
  var query=new ProjectMemberFilterPageReqVO();query.setField(field);
  controller.memberFilterOptions(query,new MockHttpServletRequest());
  var roles=switch(field){
   case MANAGER -> cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.MANAGEMENT_CODES;
   case SERVICE_MANAGER -> cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES;
   case SALES -> cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SALES_CODES;
  };
  verify(api).page(new ProjectMemberRoleSelectionApi.Query(query.getPageNo(),query.getPageSize(),null,roles));
 }

}
