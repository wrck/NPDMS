package cn.iocoder.yudao.module.pms.project.controller.admin.customercontact;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact.CustomerContactDO;
import cn.iocoder.yudao.module.pms.project.service.customercontact.CustomerContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 客户联系人")
@RestController
@RequestMapping("/pms/customer-contact")
@Validated
public class CustomerContactController {

    @Resource
    private CustomerContactService customerContactService;

    @PostMapping("/create")
    @Operation(summary = "创建客户联系人")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:create')")
    public CommonResult<Long> createCustomerContact(@Valid @RequestBody CustomerContactSaveReqVO createReqVO) {
        return success(customerContactService.createCustomerContact(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新客户联系人")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:update')")
    public CommonResult<Boolean> updateCustomerContact(@Valid @RequestBody CustomerContactSaveReqVO updateReqVO) {
        customerContactService.updateCustomerContact(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除客户联系人")
    @Parameter(name = "id", description = "联系人编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:delete')")
    public CommonResult<Boolean> deleteCustomerContact(@RequestParam("id") Long id) {
        customerContactService.deleteCustomerContact(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得客户联系人")
    @Parameter(name = "id", description = "联系人编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<CustomerContactRespVO> getCustomerContact(@RequestParam("id") Long id) {
        CustomerContactDO contact = customerContactService.getCustomerContact(id);
        return success(BeanUtils.toBean(contact, CustomerContactRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得客户联系人分页")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<PageResult<CustomerContactRespVO>> getCustomerContactPage(@Validated CustomerContactPageReqVO pageReqVO) {
        PageResult<CustomerContactDO> pageResult = customerContactService.getCustomerContactPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CustomerContactRespVO.class));
    }

    @GetMapping("/list-by-customer")
    @Operation(summary = "根据客户编号获取联系人列表")
    @Parameter(name = "customerId", description = "客户编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<List<CustomerContactRespVO>> getContactListByCustomerId(
            @RequestParam("customerId") Long customerId) {
        List<CustomerContactDO> list = customerContactService.getContactListByCustomerId(customerId);
        return success(BeanUtils.toBean(list, CustomerContactRespVO.class));
    }

}
