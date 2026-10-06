package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.Arrays;
import java.util.Set;

/** Existing native Owners/states; these source codes do not create delivery obligations. */
@Getter @RequiredArgsConstructor
public enum NativeAttachmentKind {
    CONFIGURATION("IMP", "configuration", "CONFIGURATION_LOG", "配置日志", "pms:imp-configuration", Set.of(0, 1, 3)),
    JOINT_TEST("IMP", "jointTest", "JOINT_TEST_EVIDENCE", "联调证据", "pms:imp-joint-test", Set.of(0, 1)),
    EXTERNAL_PROCUREMENT("IMP", "externalProcurement", "EXTERNAL_PROCUREMENT_ATTACHMENT", "外采附件", "pms:imp-ext-proc", Set.of(0, 4)),
    OUTSOURCE("RES", "outsourceRequest", "OUTSOURCE_ATTACHMENT", "外包附件", "pms:res-outsource", Set.of(0, 4)),
    MATERIAL_REQUISITION("IMP", "materialRequisition", "MATERIAL_REQUISITION_ATTACHMENT", "领料附件", "pms:imp-material-req", Set.of(0, 4)),
    MATERIAL_EXCHANGE("IMP", "materialExchange", "MATERIAL_EXCHANGE_REASON", "换料原因附件", "pms:imp-material-exch", Set.of(0, 4));
    private final String module;
    private final String entityType;
    private final String purpose;
    private final String title;
    private final String permission;
    private final Set<Integer> editableStates;
    public String sourceCode() { return module + "." + purpose; }
    public boolean mutable(Integer state) { return state != null && editableStates.contains(state); }
    public static NativeAttachmentKind find(String module,String entity) {
        return Arrays.stream(values()).filter(k->k.module.equals(module)&&k.entityType.equals(entity)).findFirst().orElse(null);
    }
}
