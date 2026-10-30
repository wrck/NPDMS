package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仅测试装配的统一目录贡献者：两个演示实体经同一声明进入统一目录，
 * 由默认访问、默认执行与默认页面承载，不写任何按实体名称的分支。
 * 演示公告要求 demo:notice:manage 权限；演示备忘要求 demo:memo:manage 权限，
 * 用于验证不同用户可见集合与可执行原因由服务端返回。
 */
@Component
public class DemoEntityContributor implements BusinessModelContributor {

    private final DemoNoticeMapper noticeMapper;
    private final DemoMemoMapper memoMapper;
    private final DemoTicketMapper ticketMapper;
    private final DemoTicketRevisionMapper ticketRevisionMapper;
    private final DemoRequisitionMapper requisitionMapper;
    private final DemoRequisitionRevisionMapper requisitionRevisionMapper;

    public DemoEntityContributor(DemoNoticeMapper noticeMapper, DemoMemoMapper memoMapper,
                                 DemoTicketMapper ticketMapper, DemoTicketRevisionMapper ticketRevisionMapper,
                                 DemoRequisitionMapper requisitionMapper,
                                 DemoRequisitionRevisionMapper requisitionRevisionMapper) {
        this.noticeMapper = noticeMapper;
        this.memoMapper = memoMapper;
        this.ticketMapper = ticketMapper;
        this.ticketRevisionMapper = ticketRevisionMapper;
        this.requisitionMapper = requisitionMapper;
        this.requisitionRevisionMapper = requisitionRevisionMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    private static List<BusinessOperationDescriptor> defaultOperations() {
        return List.of(
                new BusinessOperationDescriptor("create", 1, "创建",
                        BusinessOperationDescriptor.StandardOperationKind.CREATE),
                new BusinessOperationDescriptor("save", 1, "保存",
                        BusinessOperationDescriptor.StandardOperationKind.UPDATE));
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        BusinessModelDescriptor notice = new BusinessModelDescriptor("demo", "notice",
                "DEMO_NOTICE", 1, BusinessModelKind.AGGREGATE_ROOT, "演示公告",
                "demo:notice:manage",
                List.of(required("title", "标题", EntityField.Type.TEXT),
                        field("content", "内容", EntityField.Type.TEXT),
                        field("level", "级别", EntityField.Type.NUMBER),
                        field("pinned", "置顶", EntityField.Type.BOOLEAN),
                        field("publishedAt", "发布日期", EntityField.Type.DATE)),
                List.of(), defaultOperations(),
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY,
                        null, false)),
                "demo_notice");
        BusinessModelDescriptor memo = new BusinessModelDescriptor("demo", "memo",
                "DEMO_MEMO", 1, BusinessModelKind.AGGREGATE_ROOT, "演示备忘",
                "demo:memo:manage",
                List.of(required("subject", "主题", EntityField.Type.TEXT),
                        field("detail", "说明", EntityField.Type.TEXT),
                        field("dueDate", "截止日期", EntityField.Type.DATE),
                        field("archived", "归档", EntityField.Type.BOOLEAN)),
                List.of(), defaultOperations(),
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY,
                        null, false)),
                "demo_memo");
        BusinessModelDescriptor ticket = new BusinessModelDescriptor("demo", "ticket",
                "DEMO_TICKET", 1, BusinessModelKind.AGGREGATE_ROOT, "演示工单",
                "demo:ticket:manage",
                List.of(required("summary", "摘要", EntityField.Type.TEXT),
                        field("detail", "详情", EntityField.Type.TEXT),
                        field("priority", "优先级", EntityField.Type.NUMBER),
                        field("handled", "已受理", EntityField.Type.BOOLEAN)),
                List.of(), defaultOperations(),
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY,
                        null, true),
                        new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,
                                null, true),
                        new BusinessCapabilityBinding(BusinessCapabilityType.APPROVAL,
                                null, true)),
                "demo_ticket");
        BusinessModelDescriptor requisition = new BusinessModelDescriptor("demo", "requisition",
                "DEMO_REQUISITION", 1, BusinessModelKind.AGGREGATE_ROOT, "演示申领单",
                "demo:requisition:manage",
                List.of(required("title", "标题", EntityField.Type.TEXT),
                        field("quantity", "数量", EntityField.Type.NUMBER),
                        field("reason", "事由", EntityField.Type.TEXT),
                        field("urgent", "加急", EntityField.Type.BOOLEAN)),
                List.of(), defaultOperations(),
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY,
                        null, true),
                        new BusinessCapabilityBinding(BusinessCapabilityType.DYNAMIC_FORM,
                                null, true),
                        new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,
                                null, true),
                        new BusinessCapabilityBinding(BusinessCapabilityType.APPROVAL,
                                null, true)),
                "demo_requisition");
        return List.of(
                new BusinessModelDeclaration(notice, DemoNoticeEntity.class, noticeMapper, null),
                new BusinessModelDeclaration(memo, DemoMemoEntity.class, memoMapper, null),
                new BusinessModelDeclaration(ticket, DemoTicketEntity.class, ticketMapper,
                        ticketRevisionMapper),
                new BusinessModelDeclaration(requisition, DemoRequisitionEntity.class, requisitionMapper,
                        requisitionRevisionMapper));
    }
}
