package cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.service.servicelevel.CustomerServiceLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 客户服务等级")
@RestController
@RequestMapping("/pms/service-level")
@Validated
public class CustomerServiceLevelController {

    @Resource
    private CustomerServiceLevelService customerServiceLevelService;
    @Resource
    private CustomerMapper customerMapper;

    @PostMapping("/create")
    @Operation(summary = "创建客户服务等级")
    @PreAuthorize("@ss.hasPermission('pms:service-level:create')")
    public CommonResult<Long> createCustomerServiceLevel(@Valid @RequestBody CustomerServiceLevelSaveReqVO createReqVO) {
        return success(customerServiceLevelService.createCustomerServiceLevel(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新客户服务等级")
    @PreAuthorize("@ss.hasPermission('pms:service-level:update')")
    public CommonResult<Boolean> updateCustomerServiceLevel(@Valid @RequestBody CustomerServiceLevelSaveReqVO updateReqVO) {
        customerServiceLevelService.updateCustomerServiceLevel(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除客户服务等级")
    @Parameter(name = "id", description = "服务等级编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:service-level:delete')")
    public CommonResult<Boolean> deleteCustomerServiceLevel(@RequestParam("id") Long id) {
        customerServiceLevelService.deleteCustomerServiceLevel(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得客户服务等级")
    @Parameter(name = "id", description = "服务等级编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:service-level:query')")
    public CommonResult<CustomerServiceLevelRespVO> getCustomerServiceLevel(@RequestParam("id") Long id) {
        CustomerServiceLevelDO serviceLevel = customerServiceLevelService.getCustomerServiceLevel(id);
        CustomerServiceLevelRespVO respVO = BeanUtils.toBean(serviceLevel, CustomerServiceLevelRespVO.class);
        if (respVO != null && respVO.getCustomerId() != null) {
            CustomerDO customer = customerMapper.selectById(respVO.getCustomerId());
            if (customer != null) {
                respVO.setCustomerName(customer.getName());
            }
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得客户服务等级分页")
    @PreAuthorize("@ss.hasPermission('pms:service-level:query')")
    public CommonResult<PageResult<CustomerServiceLevelRespVO>> getCustomerServiceLevelPage(
            @Validated CustomerServiceLevelPageReqVO pageReqVO) {
        PageResult<CustomerServiceLevelDO> pageResult = customerServiceLevelService.getCustomerServiceLevelPage(pageReqVO);
        PageResult<CustomerServiceLevelRespVO> respPage = BeanUtils.toBean(pageResult, CustomerServiceLevelRespVO.class);
        // 批量填充客户名称
        if (respPage != null && respPage.getList() != null && !respPage.getList().isEmpty()) {
            Set<Long> customerIds = respPage.getList().stream()
                    .map(CustomerServiceLevelRespVO::getCustomerId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!customerIds.isEmpty()) {
                Map<Long, String> customerNameMap = customerMapper.selectByIds(customerIds).stream()
                        .collect(Collectors.toMap(CustomerDO::getId, CustomerDO::getName));
                respPage.getList().forEach(vo -> vo.setCustomerName(customerNameMap.get(vo.getCustomerId())));
            }
        }
        return success(respPage);
    }

}
