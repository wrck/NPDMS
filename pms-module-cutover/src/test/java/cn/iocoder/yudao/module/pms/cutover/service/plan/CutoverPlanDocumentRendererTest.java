package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverSupportArrangementDO;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CutoverPlanDocumentRendererTest {
    @Test
    void exportedWordContainsSeparateTableColumnsAndRollbackDescription() throws Exception {
        var root = JsonUtils.parseTree("""
                {"editMode":"ONLINE_TEMPLATE_STANDARD","rollbackDescription":"切回原设备验证业务",
                "overview":{"deviceSummary":[{"deviceId":50,"serialNumber":"SN-001"}],
                "deviceDetails":[{"deviceId":50,"productModel":"AR6280","currentVersion":"R1","targetVersion":"R2","remark":"替换设备"}]},
                "stepDetails":[{"sectionCode":"OPERATION","stepNo":1,"details":{"stepName":"链路切换",
                "testResult":"","businessImpactTime":"约30分钟","operator":"现场工程师","operationTime":"00:10"}},
                {"sectionCode":"PRE_OPERATION","stepNo":1,"details":{"stepName":"","testResult":"已验证备份",
                "businessImpactTime":"","operator":"准备工程师","operationTime":""}}]}
                """);
        var operation = new CutoverPlanStepDO(); operation.setSectionCode("OPERATION"); operation.setStepNo(1); operation.setContent("切换业务流量\n复核关键链路");
        var preparation = new CutoverPlanStepDO(); preparation.setSectionCode("PRE_OPERATION"); preparation.setStepNo(1); preparation.setContent("备份配置");
        var rollback = new CutoverPlanStepDO(); rollback.setSectionCode("ROLLBACK"); rollback.setStepNo(1); rollback.setContent("恢复光纤连接");
        var task = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO(); task.setTaskName("表格方案");
        var plan = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO();
        plan.setRevisionNo(1); plan.setGradeCode("C");
        for (var details : root.path("stepDetails")) {
            var matching = List.of(operation, rollback, preparation).stream().filter(row ->
                    row.getSectionCode().equals(details.path("sectionCode").asText()) && row.getStepNo() == details.path("stepNo").asInt()).findFirst().orElseThrow();
            cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec.applyDetails(matching,
                    JsonUtils.parseObject(details.get("details").toString(), cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec.PlanStepDetails.class));
        }
        ((tools.jackson.databind.node.ObjectNode) root).remove("stepDetails");
        byte[] bytes = CutoverPlanDocumentRenderer.render(CutoverPlanDocumentRenderer.RenderContext.builder().task(task).plan(plan)
                .content((tools.jackson.databind.node.ObjectNode) root).steps(List.of(operation, rollback, preparation)).supports(List.of()).fileName(ignored -> "附件").build());
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var text = new XWPFWordExtractor(document)) {
            assertThat(text.getText()).contains("割接步骤", "链路切换", "操作内容", "切换业务流量", "业务影响时间", "约30分钟",
                    "实施人员", "现场工程师", "操作时间", "00:10", "测试结果", "已验证备份", "责任人", "准备工程师", "切回原设备验证业务",
                    "AR6280", "R1", "R2", "替换设备");
            assertThat(text.getText()).doesNotContain("stepDetails", "businessImpactTime");
            assertThat(text.getText()).contains("复核关键链路");
            assertThat(document.getDocument().xmlText()).contains("<w:br");
        }
    }
    @Test
    void exportedWordContainsBusinessContentAndExactFileVersion() throws Exception {
        var root = JsonUtils.parseTree("""
                {"overview":{"projectDescription":"客户网络割接背景","scheduleTable":[{"sequenceNo":1,"plannedAt":1791806400000,"content":"准备割接"}],
                "deviceSummary":[{"serialNumber":"SN-001","deviceTypeCode":"FW"}],"preTopologyFile":{"artifactId":"970000000000442011","versionNo":2}},
                "riskMitigations":[{"riskFact":{"itemName":"链路检查","factDescription":"备用链路不可用"},"mitigation":"安排现场备件"}]}
                """);
        var step = new CutoverPlanStepDO(); step.setSectionCode("ROLLBACK"); step.setStepNo(1); step.setContent("恢复原配置");
        var support = new CutoverSupportArrangementDO(); support.setRoleCode("DP_SECOND_LINE"); support.setPersonName("张工程师");
        support.setPhone("010-12345678"); support.setDutyDescription("远程保障"); support.setArrivalTime(LocalDateTime.of(2026, 10, 12, 18, 30));
        var task = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO();
        task.setTaskName("现场割接");
        var plan = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO();
        plan.setRevisionNo(3); plan.setGradeCode("B");
        byte[] bytes = CutoverPlanDocumentRenderer.render(CutoverPlanDocumentRenderer.RenderContext.builder().task(task).plan(plan)
                .content((tools.jackson.databind.node.ObjectNode) root).steps(List.of(step)).supports(List.of(support)).fileName(ignored -> "原网络拓扑.png").build());
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var text = new XWPFWordExtractor(document)) {
            assertThat(text.getText()).contains("客户网络割接背景", "准备割接", "SN-001", "原网络拓扑.png（V2）", "链路检查", "备用链路不可用", "安排现场备件", "恢复原配置", "二线", "远程保障", "2026-10-12 18:30");
            assertThat(text.getText()).doesNotContain("970000000000442011", "DP_SECOND_LINE");
        }
    }
}
