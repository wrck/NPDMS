package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService;
import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/pms/solutions")
public class SolutionCustomerDocumentController {
 private final SolutionCustomerDocumentService documents;
 @PostMapping("/{id}/customer-files") @PreAuthorize("@ss.hasPermission('pms:sol-solution:update')")
 public CommonResult<SolutionCustomerDocumentService.Attached> attach(@PathVariable("id")Long id,@RequestBody SolutionCustomerDocumentService.Attach request){return CommonResult.success(documents.attach(id,request));}
 @GetMapping({"/{id}/customer-files/{materialId}","/{id}/customer-files/{materialId}/{fileName}"}) @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
 public CommonResult<String> download(@PathVariable("id")Long id,@PathVariable("materialId")Long material){return CommonResult.success(documents.download(id,material));}
}
