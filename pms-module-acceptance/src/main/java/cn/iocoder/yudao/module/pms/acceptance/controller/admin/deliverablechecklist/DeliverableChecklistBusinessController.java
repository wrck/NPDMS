package cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist;
import cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist.DeliverableChecklistBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.controller.DefaultBusinessController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequestMapping("/api/v1/pms/deliverable-checklists/business")
public class DeliverableChecklistBusinessController extends DefaultBusinessController {
    public DeliverableChecklistBusinessController(DeliverableChecklistBusinessService service) { super(service, "ACC", "deliverableChecklist"); }
}
