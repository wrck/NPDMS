package cn.iocoder.yudao.module.pms.platform.controller.admin.definition;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.definition.ProcessDefinitionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.definition.query.ProcessDefinitionPageQuery;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.definition.ProcessDefinitionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 中性过程定义（模板）管理入口：创建草稿、发布冻结版本、查询；
 * 语义与字段条件均以统一目录为准，执行实现只消费已发布快照。
 */
@RestController
@RequestMapping("/api/v1/pms/process-definitions")
@Tag(name = "管理后台 - PMS 中性过程定义")
@Validated
@RequiredArgsConstructor
public class ProcessDefinitionController {

    private final ProcessDefinitionService definitionService;

    /** 创建请求：字段条件以统一目录字段编码声明；版本快照在创建/发布时由服务端固化。 */
    @Data
    public static class DefinitionCreateReqVO {

        @NotBlank
        private String definitionCode;

        @NotBlank
        private String name;

        @NotBlank
        private String ownerModule;

        @NotBlank
        private String entityType;

        @NotBlank
        private String operationCode;

        @NotBlank
        private String ruleCode;

        private String ruleVersion;

        @NotEmpty
        private List<ConditionVO> conditions;

        @NotBlank
        private String resultType;

        @Data
        public static class ConditionVO {

            @NotBlank
            private String fieldCode;

            @NotBlank
            private String operator;

            private List<Object> values;
        }
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:process-definition:operate')")
    public CommonResult<ProcessDefinitionDO> create(@Valid @RequestBody DefinitionCreateReqVO reqVO) {
        // 调用方未声明规则语义版本时按 FIELD_CONDITION v1 固化，与绑定后端唯一实现一致。
        String ruleVersion = reqVO.getRuleVersion() == null || reqVO.getRuleVersion().isBlank()
                ? "1" : reqVO.getRuleVersion();
        return success(definitionService.create(reqVO.getDefinitionCode(), reqVO.getName(),
                reqVO.getOwnerModule(), reqVO.getEntityType(), reqVO.getOperationCode(),
                reqVO.getRuleCode(), ruleVersion,
                reqVO.getConditions().stream()
                        .map(condition -> new BusinessFieldFilter(condition.getFieldCode(),
                                BusinessFieldFilter.Operator.valueOf(condition.getOperator()), condition.getValues()))
                        .toList(),
                reqVO.getResultType()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:operate')")
    public CommonResult<ProcessDefinitionDO> publish(@PathVariable Long id) {
        return success(definitionService.publish(id));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:query')")
    public CommonResult<PageResult<ProcessDefinitionDO>> page(
            @RequestParam(required = false) String definitionCode,
            @RequestParam(required = false) String ownerModule,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status,
            ProcessDefinitionPageQuery pageQuery) {
        pageQuery.setDefinitionCode(definitionCode);
        pageQuery.setOwnerModule(ownerModule);
        pageQuery.setEntityType(entityType);
        pageQuery.setStatus(status);
        return success(definitionService.page(pageQuery));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:query')")
    public CommonResult<ProcessDefinitionDO> get(@PathVariable Long id) {
        return success(definitionService.requireById(id));
    }
}
