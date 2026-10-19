package cn.iocoder.yudao.module.pms.integration.sync;
public final class DppmsDirectProjectFactsTemplate {
 static final String ADAPTER="DPPMS_PROJECT_FACTS_DIRECT",PARTY="pm_project_related_party",STATE="pm_project_state";
 private DppmsDirectProjectFactsTemplate() {}
 public static SyncDefinition create(Long connectionId) {
  if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目事实来源连接无效");
  try(var input=new org.springframework.core.io.ClassPathResource("migration/dppms/tasks/P19-direct.json").getInputStream()) {
   return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
  }catch(java.io.IOException error){throw new IllegalStateException("项目事实直写定义缺失",error);}
 }
}
