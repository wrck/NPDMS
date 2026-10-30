package cn.iocoder.yudao.module.pms.project.controller.admin.projects;
import org.junit.jupiter.api.Test;import org.springframework.web.bind.annotation.GetMapping;import org.springframework.security.access.prepost.PreAuthorize;import static org.junit.jupiter.api.Assertions.*;
class ProjectMemberFilterRouteContractTest {
 @Test void projectMemberFilterHasItsOwnProjectQueryProtectedEndpoint(){
  var method=java.util.Arrays.stream(ProjectSelectionController.class.getMethods()).filter(m->m.getName().equals("memberFilterOptions")).findFirst();
  assertTrue(method.isPresent(),"Project-member filters must not use the department-scoped system simple-list");
  assertArrayEquals(new String[]{"/member-filter-options"},method.get().getAnnotation(GetMapping.class).value());
  assertEquals("@ss.hasPermission('pms:project:query')",method.get().getAnnotation(PreAuthorize.class).value());
 }
}
