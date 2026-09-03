package cn.iocoder.yudao.module.pms.asset.controller.admin.producttype;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo.ImportAssetProductTypeReqVO;
import cn.iocoder.yudao.module.pms.asset.service.producttype.AssetProductTypeImportService;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产品类型受控导入")
@RestController
@RequestMapping("/pms/asset-product-types")
@Validated
@RequiredArgsConstructor
public class AssetProductTypeImportController {

    private final AssetProductTypeImportService importService;

    @PostMapping("/actions/controlled-import")
    @Operation(summary = "受控导入产品类型来源事实")
    @PreAuthorize("@ss.hasPermission('pms:asset-product-type:controlled-import')")
    public CommonResult<ImportAssetProductTypeResult> controlledImport(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ImportAssetProductTypeReqVO reqVO) {
        List<DeviceCurrentProductTypeInput> devices = reqVO.getDevices() == null ? List.of()
                : reqVO.getDevices().stream().map(device -> new DeviceCurrentProductTypeInput(
                        device.getDeviceId(), device.getResolutionStatus())).toList();
        return success(importService.importProductType(new ImportAssetProductTypeCommand(
                reqVO.getOperationId(), idempotencyKey, reqVO.getProductTypeCode(), reqVO.getDisplayName(),
                Boolean.TRUE.equals(reqVO.getEnabled()), reqVO.getSourceSystem(), reqVO.getSourceKey(),
                reqVO.getSourceVersion(), reqVO.getSourceUpdatedAt(), reqVO.getPayloadHash(), devices)));
    }
}
