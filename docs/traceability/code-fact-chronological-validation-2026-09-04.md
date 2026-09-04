# 三分支按时间重放验证结果

- 分支Head：`a9bb366e9efc5dab9f867fece5e04b0a075079b2`
- git diff --check：`0`
- Maven全Reactor package（含测试编译，跳过测试执行）：`1`
- 前端corepack：`0`
- 前端依赖安装：`0`
- 前端typecheck：`1`
- 前端生产构建：`1`

## Maven日志尾部
```text
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[305,71] cannot find symbol
  symbol:   method getCurrentPublishedRevisionId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[306,46] cannot find symbol
  symbol:   method getStatusCode()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[310,14] cannot find symbol
  symbol:   method setId(long)
  location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[312,37] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[313,35] cannot find symbol
  symbol:   method getRevisionNo()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[314,14] cannot find symbol
  symbol:   method setStatusCode(java.lang.String)
  location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[315,14] cannot find symbol
  symbol:   method setDraftMarker(int)
  location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[316,41] cannot find symbol
  symbol:   method getId()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[317,37] cannot find symbol
  symbol:   method getFormConfJson()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[318,38] cannot find symbol
  symbol:   method getFormRulesJson()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[319,35] cannot find symbol
  symbol:   method getEngineCode()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[320,40] cannot find symbol
  symbol:   method getDesignerVersion()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[321,40] cannot find symbol
  symbol:   method getRendererVersion()
  location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[322,14] cannot find symbol
  symbol:   method setVersion(int)
  location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[328,115] cannot find symbol
  symbol:   method getId()
  location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[333,81] cannot find symbol
  symbol:   method getTemplateId()
  location: variable inspected of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[336,53] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[338,48] cannot find symbol
  symbol:   method getFormConfJson()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[338,76] cannot find symbol
  symbol:   method getFormRulesJson()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,25] cannot find symbol
  symbol:   method getEngineCode()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,51] cannot find symbol
  symbol:   method getDesignerVersion()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,82] cannot find symbol
  symbol:   method getRendererVersion()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[342,25] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[342,43] cannot find symbol
  symbol:   method getId()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[345,25] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[345,43] cannot find symbol
  symbol:   method getVersion()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[346,44] cannot find symbol
  symbol:   method getId()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[349,103] cannot find symbol
  symbol:   method getId()
  location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[350,93] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[351,58] cannot find symbol
  symbol:   method getId()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[351,75] cannot find symbol
  symbol:   method getVersion()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[352,24] cannot find symbol
  symbol:   method getAvailabilityCode()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[354,32] cannot find symbol
  symbol:   method getCurrentDraftRevisionId()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[354,77] cannot find symbol
  symbol:   method getCurrentPublishedRevisionId()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[353,83] cannot find symbol
  symbol:   method getAvailabilityCode()
  location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[360,32] cannot find symbol
  symbol:   method getVersion()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[361,36] cannot find symbol
  symbol:   method getAvailabilityCode()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[364,71] cannot find symbol
  symbol:   method getCurrentPublishedRevisionId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[368,25] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[368,43] cannot find symbol
  symbol:   method getVersion()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[373,25] cannot find symbol
  symbol:   method getId()
  location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[INFO] 100 errors 
[INFO] -------------------------------------------------------------
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/task/BpmProcessInstanceConvert.java:[124,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/definition/BpmProcessDefinitionConvert.java:[97,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-bpm ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 19 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-bpm ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-bpm ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/target/yudao-module-bpm-2026.06-jdk25-SNAPSHOT.jar
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao-dependencies ................................. SUCCESS [  1.862 s]
[INFO] yudao .............................................. SUCCESS [  0.718 s]
[INFO] yudao-framework .................................... SUCCESS [  0.099 s]
[INFO] yudao-common ....................................... SUCCESS [ 37.579 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [ 11.700 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [ 10.976 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [01:01 min]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [ 15.887 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [ 35.617 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  4.629 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  1.098 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  5.391 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [ 10.560 s]
[INFO] yudao-spring-boot-starter-protection ............... SUCCESS [ 10.990 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  1.226 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [ 10.831 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  6.209 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.672 s]
[INFO] yudao-module-infra ................................. SUCCESS [ 31.002 s]
[INFO] yudao-module-system ................................ SUCCESS [ 27.091 s]
[INFO] yudao-module-bpm ................................... SUCCESS [  8.536 s]
[INFO] pms-module-customer-api ............................ SUCCESS [ 15.018 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  5.717 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.927 s]
[INFO] pms-module-asset-api ............................... SUCCESS [  0.995 s]
[INFO] pms-module-customer ................................ SUCCESS [  2.950 s]
[INFO] pms-module-engineering-api ......................... SUCCESS [ 11.267 s]
[INFO] pms-module-commerce-api ............................ SUCCESS [ 11.160 s]
[INFO] pms-module-integration-api ......................... SUCCESS [ 10.116 s]
[INFO] pms-module-platform ................................ FAILURE [  4.604 s]
[INFO] pms-module-project ................................. SKIPPED
[INFO] pms-module-engineering ............................. SKIPPED
[INFO] pms-module-cutover-api ............................. SUCCESS [  0.968 s]
[INFO] pms-module-cutover ................................. SKIPPED
[INFO] pms-module-service ................................. SKIPPED
[INFO] pms-module-asset ................................... SKIPPED
[INFO] pms-module-commerce ................................ SKIPPED
[INFO] pms-module-outsourcing ............................. SUCCESS [  0.462 s]
[INFO] pms-module-analytics ............................... SUCCESS [  0.421 s]
[INFO] pms-module-integration ............................. SUCCESS [ 27.254 s]
[INFO] yudao-server ....................................... SKIPPED
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  03:42 min (Wall Clock)
[INFO] Finished at: 2026-09-04T03:56:14Z
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile (default-compile) on project pms-module-platform: Compilation failure: Compilation failure: 
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileBusinessObjectPolicyRegistry.java:[267,43] method requireBusinessGrantFact(cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadPolicyFact,java.lang.Long,java.lang.Integer,java.lang.Long,java.lang.String,java.lang.Long,java.lang.String,java.lang.String,java.lang.Integer,java.lang.Long) is already defined in class cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileBusinessObjectPolicyRegistry.java:[289,51] method requireAuthenticatedAssistedFact(cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadPolicyFact,java.lang.Long,java.lang.Long,java.lang.Long,java.lang.String,java.lang.Long,java.lang.String,java.lang.String,java.lang.Integer,java.lang.Long) is already defined in class cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[36,30] cannot find symbol
[ERROR]   symbol:   method getSecret()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[39,64] cannot find symbol
[ERROR]   symbol:   method getCredentialCode()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[39,91] cannot find symbol
[ERROR]   symbol:   method getCredentialType()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[40,26] cannot find symbol
[ERROR]   symbol:   method getUsername()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[40,55] cannot find symbol
[ERROR]   symbol:   method getKmsReference()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[40,80] cannot find symbol
[ERROR]   symbol:   method getDeviceId()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[41,26] cannot find symbol
[ERROR]   symbol:   method getCommandTemplateId()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java:[41,56] cannot find symbol
[ERROR]   symbol:   method getExpiresAt()
[ERROR]   location: variable reqVO of type cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/DynamicFormInstanceController.java:[72,72] cannot find symbol
[ERROR]   symbol:   method getTemplateRevisionId()
[ERROR]   location: variable request of type cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/DynamicFormInstanceController.java:[73,32] cannot find symbol
[ERROR]   symbol:   method getExpectedTemplateVersion()
[ERROR]   location: variable request of type cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/DynamicFormInstanceController.java:[73,70] cannot find symbol
[ERROR]   symbol:   method getInstanceName()
[ERROR]   location: variable request of type cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreateReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/DynamicFormInstanceController.java:[91,32] cannot find symbol
[ERROR]   symbol:   method getValues()
[ERROR]   location: variable request of type cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePatchReqVO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[106,35] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[108,78] cannot find symbol
[ERROR]   symbol:   method getTemplateName()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[110,77] cannot find symbol
[ERROR]   symbol:   method getCategoryCode()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[112,77] cannot find symbol
[ERROR]   symbol:   method getDescription()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[114,28] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[120,28] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[122,60] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[121,50] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[121,88] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[125,28] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[163,56] cannot find symbol
[ERROR]   symbol:   method getTemplateId()
[ERROR]   location: variable inspected of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[165,95] cannot find symbol
[ERROR]   symbol:   method getTemplateId()
[ERROR]   location: variable inspected of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[168,25] cannot find symbol
[ERROR]   symbol:   method setFormConfJson(java.lang.String)
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[169,25] cannot find symbol
[ERROR]   symbol:   method setFormRulesJson(java.lang.String)
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[170,25] cannot find symbol
[ERROR]   symbol:   method setEngineCode(java.lang.String)
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[171,25] cannot find symbol
[ERROR]   symbol:   method setDesignerVersion(java.lang.String)
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[172,25] cannot find symbol
[ERROR]   symbol:   method setRendererVersion(java.lang.String)
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[176,33] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[179,65] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[177,81] cannot find symbol
[ERROR]   symbol:   method getTemplateId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[178,55] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[178,98] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[184,33] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[267,17] cannot find symbol
[ERROR]   symbol:   method setId(long)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[269,17] cannot find symbol
[ERROR]   symbol:   method setTemplateCode(java.lang.String)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[270,17] cannot find symbol
[ERROR]   symbol:   method setTemplateName(java.lang.String)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[271,17] cannot find symbol
[ERROR]   symbol:   method setCategoryCode(java.lang.String)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[272,17] cannot find symbol
[ERROR]   symbol:   method setDescription(java.lang.String)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[273,17] cannot find symbol
[ERROR]   symbol:   method setAvailabilityCode(java.lang.String)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[274,17] cannot find symbol
[ERROR]   symbol:   method setVersion(int)
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[281,14] cannot find symbol
[ERROR]   symbol:   method setId(long)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[283,37] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[284,14] cannot find symbol
[ERROR]   symbol:   method setRevisionNo(int)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[285,14] cannot find symbol
[ERROR]   symbol:   method setStatusCode(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[286,14] cannot find symbol
[ERROR]   symbol:   method setDraftMarker(int)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[287,14] cannot find symbol
[ERROR]   symbol:   method setFormConfJson(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[288,14] cannot find symbol
[ERROR]   symbol:   method setFormRulesJson(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[289,14] cannot find symbol
[ERROR]   symbol:   method setEngineCode(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[290,14] cannot find symbol
[ERROR]   symbol:   method setDesignerVersion(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[291,14] cannot find symbol
[ERROR]   symbol:   method setRendererVersion(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[292,14] cannot find symbol
[ERROR]   symbol:   method setVersion(int)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[294,98] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[300,32] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[302,25] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[305,53] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[305,71] cannot find symbol
[ERROR]   symbol:   method getCurrentPublishedRevisionId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[306,46] cannot find symbol
[ERROR]   symbol:   method getStatusCode()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[310,14] cannot find symbol
[ERROR]   symbol:   method setId(long)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[312,37] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[313,35] cannot find symbol
[ERROR]   symbol:   method getRevisionNo()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[314,14] cannot find symbol
[ERROR]   symbol:   method setStatusCode(java.lang.String)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[315,14] cannot find symbol
[ERROR]   symbol:   method setDraftMarker(int)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[316,41] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[317,37] cannot find symbol
[ERROR]   symbol:   method getFormConfJson()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[318,38] cannot find symbol
[ERROR]   symbol:   method getFormRulesJson()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[319,35] cannot find symbol
[ERROR]   symbol:   method getEngineCode()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[320,40] cannot find symbol
[ERROR]   symbol:   method getDesignerVersion()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[321,40] cannot find symbol
[ERROR]   symbol:   method getRendererVersion()
[ERROR]   location: variable source of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[322,14] cannot find symbol
[ERROR]   symbol:   method setVersion(int)
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[328,115] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable draft of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[333,81] cannot find symbol
[ERROR]   symbol:   method getTemplateId()
[ERROR]   location: variable inspected of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[336,53] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[338,48] cannot find symbol
[ERROR]   symbol:   method getFormConfJson()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[338,76] cannot find symbol
[ERROR]   symbol:   method getFormRulesJson()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,25] cannot find symbol
[ERROR]   symbol:   method getEngineCode()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,51] cannot find symbol
[ERROR]   symbol:   method getDesignerVersion()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[339,82] cannot find symbol
[ERROR]   symbol:   method getRendererVersion()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[342,25] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[342,43] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[345,25] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[345,43] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[346,44] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[349,103] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable revision of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[350,93] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[351,58] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[351,75] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[352,24] cannot find symbol
[ERROR]   symbol:   method getAvailabilityCode()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[354,32] cannot find symbol
[ERROR]   symbol:   method getCurrentDraftRevisionId()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[354,77] cannot find symbol
[ERROR]   symbol:   method getCurrentPublishedRevisionId()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[353,83] cannot find symbol
[ERROR]   symbol:   method getAvailabilityCode()
[ERROR]   location: variable updated of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[360,32] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[361,36] cannot find symbol
[ERROR]   symbol:   method getAvailabilityCode()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[364,71] cannot find symbol
[ERROR]   symbol:   method getCurrentPublishedRevisionId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[368,25] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[368,43] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java:[373,25] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable template of type cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :pms-module-platform
```

## 前端typecheck日志尾部
```text
 ERR_PNPM_NO_SCRIPT  Missing script: typecheck

Command "typecheck" not found. Did you mean "pnpm run ts:check"?
```

## 前端构建日志尾部
```text

> yudao-ui-admin-vue3@2026.06-snapshot build:prod /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3
> pnpm icons:check && node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build --mode prod


> yudao-ui-admin-vue3@2026.06-snapshot icons:check /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3
> node scripts/generate-iconify-collections.cjs --check

Offline Iconify collection is current (36 collections).
[36mvite v8.1.4 [32mbuilding client environment for prod...[36m[39m
[2Ktransforming...[33m[1m(!) %VITE_APP_TITLE% is not defined in env variables found in /index.html. Is the variable mistyped?[22m[39m
[33m[1m(!) %VITE_APP_TITLE% is not defined in env variables found in /index.html. Is the variable mistyped?[22m[39m
✓ 8518 modules transformed.
[31m✗[39m Build failed in 23.86s
[31merror during build:
[31mBuild failed with 4 errors:

[plugin vite:vue] /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue:244:6
SyntaxError: [vue/compiler-sfc] Identifier 'handleApprovalWorkspaceChanged' has already been declared. (244:6)

/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue
495|    if (reassignmentQueueVisible.value) await loadReassignmentQueue()
496|  }
497|  const handleApprovalWorkspaceChanged = async () => {
   |        ^
498|    await loadPage()
499|    if (todoVisible.value) await loadApprovalTodos()
    at constructor (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:365:19)
    at TypeScriptParserMixin.raise (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:6616:19)
    at TypeScriptScopeHandler.checkRedeclarationInScope (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:1619:19)
    at TypeScriptScopeHandler.declareName (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:1585:12)
    at TypeScriptScopeHandler.declareName (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:4892:11)
    at TypeScriptParserMixin.declareNameFromIdentifier (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7584:16)
    at TypeScriptParserMixin.checkIdentifier (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7580:12)
    at TypeScriptParserMixin.checkLVal (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7517:12)
    at TypeScriptParserMixin.parseVarId (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13429:10)
    at TypeScriptParserMixin.parseVarId (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9769:11)
    at TypeScriptParserMixin.parseVar (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13400:12)
    at TypeScriptParserMixin.parseVarStatement (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13247:10)
    at TypeScriptParserMixin.parseVarStatement (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9425:31)
    at TypeScriptParserMixin.parseStatementContent (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12868:23)
    at TypeScriptParserMixin.parseStatementContent (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9525:18)
    at TypeScriptParserMixin.parseStatementLike (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12784:17)
    at TypeScriptParserMixin.parseModuleItem (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12761:17)
    at TypeScriptParserMixin.parseBlockOrModuleBlockBody (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13333:36)
    at TypeScriptParserMixin.parseBlockBody (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13326:10)
    at TypeScriptParserMixin.parseProgram (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12639:10)
    at TypeScriptParserMixin.parseTopLevel (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12629:25)
    at TypeScriptParserMixin.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:14505:25)
    at TypeScriptParserMixin.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:10143:18)
    at Object.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:14539:38)
    at parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:19874:25)
    at new ScriptCompileContext (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:19891:53)
    at Object.compileScript (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:24921:15)
    at resolveScript (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:276:36)
    at genScriptCode (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1430:17)
    at transformMain (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1312:53)
    at TransformPluginContextImpl.handler (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1714:27)
    at TransformPluginContextImpl.handler (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33254:13)
    at plugin (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/bindingify-input-options-XPJLJOD0.mjs:1511:30)
    at plugin.<computed> (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/bindingify-input-options-XPJLJOD0.mjs:1959:18)
[31m[PARSE_ERROR] [0mDuplicated export 'createArrivalWriteBarrier'
     [38;5;246m╭[0m[38;5;246m─[0m[38;5;246m[[0m src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts:114:14 [38;5;246m][0m
     [38;5;246m│[0m
 [38;5;246m114 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mcreateArrivalWriteBarrier[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249m([0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ────────────┬────────────  
 [38;5;240m    │[0m                          ╰────────────── Export has already been declared here
 [38;5;240m    │[0m 
 [38;5;246m227 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mcreateArrivalWriteBarrier[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249m([0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ────────────┬────────────  
 [38;5;240m    │[0m                          ╰────────────── It cannot be redeclared here
[38;5;246m─────╯[0m

[31m[PARSE_ERROR] [0mDuplicated export 'runArrivalGuardedWrite'
     [38;5;246m╭[0m[38;5;246m─[0m[38;5;246m[[0m src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts:135:14 [38;5;246m][0m
     [38;5;246m│[0m
 [38;5;246m135 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mrunArrivalGuardedWrite[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249ma[0m[38;5;249ms[0m[38;5;249my[0m[38;5;249mn[0m[38;5;249mc[0m[38;5;249m [0m[38;5;249m([0m[38;5;249mo[0m[38;5;249mp[0m[38;5;249mt[0m[38;5;249mi[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ───────────┬──────────  
 [38;5;240m    │[0m                         ╰──────────── Export has already been declared here
 [38;5;240m    │[0m 
 [38;5;246m248 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mrunArrivalGuardedWrite[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249ma[0m[38;5;249ms[0m[38;5;249my[0m[38;5;249mn[0m[38;5;249mc[0m[38;5;249m [0m[38;5;249m([0m[38;5;249mo[0m[38;5;249mp[0m[38;5;249mt[0m[38;5;249mi[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ───────────┬──────────  
 [38;5;240m    │[0m                         ╰──────────── It cannot be redeclared here
[38;5;246m─────╯[0m

[31m[PARSE_ERROR] [0mDuplicated export 'runArrivalIntent'
     [38;5;246m╭[0m[38;5;246m─[0m[38;5;246m[[0m src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts:181:14 [38;5;246m][0m
     [38;5;246m│[0m
 [38;5;246m181 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mrunArrivalIntent[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249ma[0m[38;5;249ms[0m[38;5;249my[0m[38;5;249mn[0m[38;5;249mc[0m[38;5;249m [0m[38;5;249m([0m[38;5;249mo[0m[38;5;249mp[0m[38;5;249mt[0m[38;5;249mi[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ────────┬───────  
 [38;5;240m    │[0m                      ╰───────── Export has already been declared here
 [38;5;240m    │[0m 
 [38;5;246m294 │[0m [38;5;249me[0m[38;5;249mx[0m[38;5;249mp[0m[38;5;249mo[0m[38;5;249mr[0m[38;5;249mt[0m[38;5;249m [0m[38;5;249mc[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249mt[0m[38;5;249m [0mrunArrivalIntent[38;5;249m [0m[38;5;249m=[0m[38;5;249m [0m[38;5;249ma[0m[38;5;249ms[0m[38;5;249my[0m[38;5;249mn[0m[38;5;249mc[0m[38;5;249m [0m[38;5;249m([0m[38;5;249mo[0m[38;5;249mp[0m[38;5;249mt[0m[38;5;249mi[0m[38;5;249mo[0m[38;5;249mn[0m[38;5;249ms[0m[38;5;249m)[0m[38;5;249m [0m[38;5;249m=[0m[38;5;249m>[0m[38;5;249m [0m[38;5;249m{[0m
 [38;5;240m    │[0m              ────────┬───────  
 [38;5;240m    │[0m                      ╰───────── It cannot be redeclared here
[38;5;246m─────╯[0m
[31m
    at aggregateBindingErrorsIntoJsError (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/error-BHRSI0R7.mjs:48:18)
    at unwrapBindingResult (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/error-BHRSI0R7.mjs:18:128)
    at #build (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/rolldown-build-CtPvmZgJ.mjs:3276:34)
    at async buildEnvironment (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33011:66)
    at async Object.build (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33433:19)
    at async Object.buildApp (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33430:153)
    at async CAC.<anonymous> (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/cli.js:776:3) {
  errors: [Getter/Setter]
}[39m
 ELIFECYCLE  Command failed with exit code 1.
```
