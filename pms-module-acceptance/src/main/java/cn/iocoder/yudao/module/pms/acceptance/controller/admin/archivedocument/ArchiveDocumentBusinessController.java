package cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument;
import cn.iocoder.yudao.module.pms.acceptance.service.archivedocument.ArchiveDocumentBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.controller.DefaultBusinessController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequestMapping("/api/v1/pms/archive-documents/business")
public class ArchiveDocumentBusinessController extends DefaultBusinessController {
    public ArchiveDocumentBusinessController(ArchiveDocumentBusinessService service) { super(service, "ACC", "archiveDocument"); }
}
