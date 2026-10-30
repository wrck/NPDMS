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
                .content((tools.jackson.databind.node.ObjectNode) root).steps(List.of(operation, rollback, preparation)).supports(List.of()).attachment(ignored -> new CutoverPlanDocumentRenderer.Attachment("附件", "application/pdf", null)).build());
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
                .content((tools.jackson.databind.node.ObjectNode) root).steps(List.of(step)).supports(List.of(support)).attachment(ignored -> new CutoverPlanDocumentRenderer.Attachment("原网络拓扑.png", "image/png", image(320, 120, "png"))).build());
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var text = new XWPFWordExtractor(document)) {
            assertThat(text.getText()).contains("客户网络割接背景", "准备割接", "SN-001", "原网络拓扑.png（V2）", "链路检查", "备用链路不可用", "安排现场备件", "恢复原配置", "二线", "远程保障", "2026-10-12 18:30");
            assertThat(text.getText()).doesNotContain("970000000000442011", "DP_SECOND_LINE");
        }
    }

    @Test
    void embedsExactPngAndJpegBytesAtTheirSectionsAndScalesWithoutStretching() throws Exception {
        byte[] png = image(1600, 600, "png"), jpeg = image(600, 1600, "jpeg");
        var root = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree("""
                {"editMode":"ONLINE_TEMPLATE_STANDARD","overview":{"projectDescription":"图片内嵌验收",
                "preTopologyFile":{"artifactId":"1","versionNo":2},"postTopologyFile":{"artifactId":"2","versionNo":3},
                "networkConfigurationFile":{"artifactId":"3","versionNo":4}}}
                """);
        var task = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO(); task.setTaskName("图片内嵌验收");
        var plan = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO(); plan.setRevisionNo(1); plan.setGradeCode("C");
        byte[] bytes = CutoverPlanDocumentRenderer.render(CutoverPlanDocumentRenderer.RenderContext.builder().task(task).plan(plan)
                .content(root).steps(List.of()).supports(List.of()).attachment(file -> switch (file.path("artifactId").asText()) {
                    case "1" -> new CutoverPlanDocumentRenderer.Attachment("原拓扑.png", "image/png", png);
                    case "2" -> new CutoverPlanDocumentRenderer.Attachment("新拓扑.jpg", "image/jpeg", jpeg);
                    default -> new CutoverPlanDocumentRenderer.Attachment("配置说明.pdf", "application/pdf", null);
                }).build());
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var text = new XWPFWordExtractor(document)) {
            assertThat(document.getAllPictures()).hasSize(2);
            assertThat(document.getAllPictures()).anySatisfy(picture -> assertThat(picture.getData()).isEqualTo(png));
            assertThat(document.getAllPictures()).anySatisfy(picture -> assertThat(picture.getData()).isEqualTo(jpeg));
            assertThat(text.getText()).contains("原拓扑.png（V2）", "新拓扑.jpg（V3）", "配置说明.pdf（V4）");
            var drawings = document.getParagraphs().stream().flatMap(p -> p.getRuns().stream()).flatMap(r -> r.getEmbeddedPictures().stream()).toList();
            assertThat(drawings).hasSize(2);
            for (int i = 0; i < drawings.size(); i++) {
                var extent = drawings.get(i).getCTPicture().getSpPr().getXfrm().getExt();
                assertThat(extent.getCx()).isLessThanOrEqualTo(org.apache.poi.util.Units.toEMU(450));
                assertThat(extent.getCy()).isLessThanOrEqualTo(org.apache.poi.util.Units.toEMU(580));
                assertThat((double) extent.getCx() / extent.getCy()).isCloseTo(i == 0 ? 1600.0 / 600 : 600.0 / 1600,
                        org.assertj.core.data.Offset.offset(0.0001));
            }
            assertThat(document.getDocument().xmlText()).contains("r:embed=").doesNotContain("r:link=");
            assertThat(document.getProperties().getCustomProperties().getProperty(CutoverPlanDocumentRenderer.FORMAT_PROPERTY).getLpwstr())
                    .isEqualTo(CutoverPlanDocumentRenderer.FORMAT_VERSION);
        }
        var output = java.nio.file.Path.of("..", ".run", "cutover-image-plan-acceptance");
        java.nio.file.Files.createDirectories(output);
        java.nio.file.Files.write(output.resolve("embedded-plan.docx"), bytes);
    }

    @Test
    void corruptImageDoesNotSilentlyGenerateAPlanWithoutItsPicture() {
        var root = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree("{\"overview\":{\"preTopologyFile\":{\"versionNo\":1}}}");
        var task = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO(); task.setTaskName("坏图");
        var plan = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CutoverPlanDocumentRenderer.render(
                CutoverPlanDocumentRenderer.RenderContext.builder().task(task).plan(plan).content(root).steps(List.of()).supports(List.of())
                        .attachment(file -> new CutoverPlanDocumentRenderer.Attachment("坏图.png", "image/png", new byte[]{1, 2, 3})).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("方案图片无法解析");
    }

    private static byte[] image(int width, int height, String format) {
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, width, height);
        graphics.setColor(new java.awt.Color(30, 110, 180)); graphics.fillRect(10, 10, width - 20, height - 20);
        graphics.setColor(java.awt.Color.WHITE); graphics.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(16, width / 25)));
        graphics.drawString(width > height ? "BEFORE CUTOVER - TOPOLOGY" : "AFTER CUTOVER", 30, 80);
        graphics.drawLine(40, height / 2, width - 40, height / 2); graphics.dispose();
        try (var output = new java.io.ByteArrayOutputStream()) { javax.imageio.ImageIO.write(image, format, output); return output.toByteArray(); }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
}
