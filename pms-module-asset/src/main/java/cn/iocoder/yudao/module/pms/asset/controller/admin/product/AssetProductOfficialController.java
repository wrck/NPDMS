package cn.iocoder.yudao.module.pms.asset.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo.ProductOfficialPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo.ProductOfficialRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产品信息（CRM 只读副本）")
@RestController
@RequestMapping("/api/v1/pms/asset-product-officials")
@Validated
public class AssetProductOfficialController {

    @Resource
    private AssetProductOfficialMapper productOfficialMapper;

    @GetMapping("/page")
    @Operation(summary = "产品信息分页：关键字跨名称/编码/型号模糊，停用状态仅供前端禁选展示")
    // 权限沿用换货申报查询：当前唯一消费方是换货产品下拉，不臆造新权限串
    @PreAuthorize("@ss.hasPermission('pms:imp-material-exch:query')")
    public CommonResult<PageResult<ProductOfficialRespVO>> getProductOfficialPage(
            @Valid ProductOfficialPageReqVO pageReqVO) {
        LambdaQueryWrapperX<AssetProductOfficialDO> wrapper =
                new LambdaQueryWrapperX<AssetProductOfficialDO>()
                        .orderByDesc(AssetProductOfficialDO::getId);
        if (StringUtils.isNotBlank(pageReqVO.getKeyword())) {
            wrapper.and(w -> w.like(AssetProductOfficialDO::getProductName, pageReqVO.getKeyword())
                    .or().like(AssetProductOfficialDO::getProductCode, pageReqVO.getKeyword())
                    .or().like(AssetProductOfficialDO::getProductModel, pageReqVO.getKeyword()));
        }
        PageResult<AssetProductOfficialDO> page = productOfficialMapper.selectPage(pageReqVO, wrapper);
        return success(new PageResult<>(BeanUtils.toBean(page.getList(), ProductOfficialRespVO.class),
                page.getTotal()));
    }
}
