package com.dp.plat.lowcode.version;

import com.dp.plat.lowcode.dto.ConfigPackageDTO;
import com.dp.plat.lowcode.dto.DependencyValidationResult;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.service.LowCodeConnectorService;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeListService;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 环境晋升服务 — 依赖校验单元测试。
 */
@DisplayName("环境晋升服务 — 依赖校验测试")
@ExtendWith(MockitoExtension.class)
class EnvironmentPromotionServiceTest {

    @Mock
    private LowCodeConfigVersionService configVersionService;
    @Mock
    private LowCodeEntityService entityService;
    @Mock
    private LowCodeFormService formService;
    @Mock
    private LowCodeListService listService;
    @Mock
    private LowCodeMicroflowService microflowService;
    @Mock
    private LowCodeConnectorService connectorService;
    @Mock
    private LowCodeRuleService ruleService;
    @Mock
    private PromotionGateService promotionGateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EnvironmentPromotionService service;

    @BeforeEach
    void setUp() {
        // 构造顺序需与 EnvironmentPromotionService 字段声明顺序一致
        service = new EnvironmentPromotionService(
                configVersionService, objectMapper, entityService, formService,
                listService, microflowService, connectorService, ruleService, promotionGateService);
    }

    /** 构造一个配置包项 */
    private ConfigPackageDTO.PackageItem item(String type, String code, String snapshot) {
        return ConfigPackageDTO.PackageItem.builder()
                .configType(type)
                .configCode(code)
                .configId(1L)
                .version(1)
                .snapshot(snapshot)
                .build();
    }

    private ConfigPackageDTO pkg(ConfigPackageDTO.PackageItem... items) {
        return ConfigPackageDTO.builder()
                .sourceEnvironment("DEV")
                .targetEnvironment("TEST")
                .items(new ArrayList<>(List.of(items)))
                .build();
    }

    @Test
    @DisplayName("包内自洽 — 所有引用在包内存在 → valid=true")
    void validatePackageDependencies_selfSatisfied_valid() {
        // FORM 引用 ENTITY 'device'，ENTITY 'device' 同在包内
        ConfigPackageDTO.PackageItem form = item("FORM", "formOrder",
                "{\"code\":\"formOrder\",\"formConfig\":{\"fields\":[{\"name\":\"qty\",\"entityCode\":\"device\"}]}}");
        ConfigPackageDTO.PackageItem entity = item("ENTITY", "device",
                "{\"code\":\"device\",\"name\":\"设备\"}");

        DependencyValidationResult result = service.validatePackageDependencies(pkg(form, entity));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMissing()).isEmpty();
    }

    @Test
    @DisplayName("缺失依赖 — FORM 引用不存在的 ENTITY → valid=false 且 missing 非空")
    void validatePackageDependencies_missingDependency_invalid() {
        // 包内只有 FORM，引用的 ENTITY 'device' 既不在包内也不在目标环境
        ConfigPackageDTO.PackageItem form = item("FORM", "formOrder",
                "{\"code\":\"formOrder\",\"formConfig\":{\"fields\":[{\"name\":\"qty\",\"entityCode\":\"device\"}]}}");
        when(entityService.count(any())).thenReturn(0L);

        DependencyValidationResult result = service.validatePackageDependencies(pkg(form));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMissing()).hasSize(1);
        DependencyValidationResult.MissingDependency md = result.getMissing().get(0);
        assertThat(md.getType()).isEqualTo("ENTITY");
        assertThat(md.getCode()).isEqualTo("device");
        assertThat(md.getReferencedBy()).isEqualTo("FORM 'formOrder'");
    }

    @Test
    @DisplayName("空包 → valid=true")
    void validatePackageDependencies_emptyPackage_valid() {
        DependencyValidationResult result = service.validatePackageDependencies(
                ConfigPackageDTO.builder().items(new ArrayList<>()).build());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMissing()).isEmpty();
    }

    @Test
    @DisplayName("JSON 解析失败 — best-effort 不抛异常")
    void validatePackageDependencies_malformedSnapshot_bestEffort() {
        // 非法 JSON 快照，且不含可被正则匹配的引用字段 → 不抛异常、无缺失
        ConfigPackageDTO.PackageItem bad = item("FORM", "formBad",
                "{ malformed json {{{ not closed");

        DependencyValidationResult result = service.validatePackageDependencies(pkg(bad));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMissing()).isEmpty();
    }

    @Test
    @DisplayName("TRIGGER 特化 — targetCode 按 targetType 解析为 MICROFLOW 引用，包内自洽 → valid=true")
    void validatePackageDependencies_triggerMicroflow_valid() {
        // TRIGGER 的 targetCode 'mfCalc' 由 targetType=MICROFLOW 决定依赖类型，目标微流在包内
        ConfigPackageDTO.PackageItem trigger = item("TRIGGER", "triggerOrder",
                "{\"code\":\"triggerOrder\",\"targetType\":\"MICROFLOW\",\"targetCode\":\"mfCalc\"}");
        ConfigPackageDTO.PackageItem microflow = item("MICROFLOW", "mfCalc",
                "{\"code\":\"mfCalc\"}");

        DependencyValidationResult result = service.validatePackageDependencies(pkg(trigger, microflow));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMissing()).isEmpty();
    }
}
