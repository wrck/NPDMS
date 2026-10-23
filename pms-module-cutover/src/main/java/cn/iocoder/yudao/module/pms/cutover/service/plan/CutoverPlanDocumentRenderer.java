package cn.iocoder.yudao.module.pms.cutover.service.plan;

import lombok.Builder;
import tools.jackson.databind.node.ObjectNode;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import org.apache.poi.xwpf.usermodel.*;
import tools.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** 将本次方案写入Word，图片附件按精确版本内嵌对应章节。 */
public final class CutoverPlanDocumentRenderer {
    public static final String FORMAT_PROPERTY = "cutover-render-format";
    public static final String FORMAT_VERSION = "images-v1";
    private CutoverPlanDocumentRenderer() { }
    public record Attachment(String name, String mediaType, byte[] content) {}
    @Builder
    public record RenderContext(CutoverTaskDO task, CutoverPlanRevisionDO plan, ObjectNode content,
                                List<CutoverPlanStepDO> steps, List<CutoverSupportArrangementDO> supports,
                                Function<JsonNode, Attachment> attachment,
                                Function<JsonNode, String> riskDescription) { }

    public static byte[] render(RenderContext context) {
        var task = context.task(); var plan = context.plan();
        var steps = context.steps(); var supports = context.supports();
        JsonNode content = new CutoverPlanContentCodec().assembleStored(
                new CutoverPlanContentCodec.StoredPlanContent(context.content(), steps));
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.getProperties().getCustomProperties().addProperty(FORMAT_PROPERTY, FORMAT_VERSION);
            paragraph(document, task.getTaskName() + "割接方案", true);
            if (content.hasNonNull("scheduledTime")) paragraph(document, "计划割接时间：" + timestamp(content.path("scheduledTime").asLong()), false);
            paragraph(document, "方案版本：" + plan.getRevisionNo() + "　割接等级：" + plan.getGradeCode(), false);
            var overview = content.path("overview");
            if (!overview.isMissingNode()) {
                paragraph(document, "项目与割接概述", true);
                paragraph(document, overview.path("projectDescription").asText(), false);
                paragraph(document, "割接计划", true);
                var schedule = document.createTable(1, 3);
                row(schedule.getRow(0), "序号", "计划时间", "事项");
                for (var value : overview.path("scheduleTable")) row(schedule.createRow(), value.path("sequenceNo").asText(),
                        timestamp(value.path("plannedAt").asLong(0)), value.path("content").asText());
                paragraph(document, "设备范围", true);
                if (overview.has("deviceDetails")) {
                    Map<String, JsonNode> detailRows = new HashMap<>();
                    overview.path("deviceDetails").forEach(value -> detailRows.put(value.path("deviceId").asText(), value));
                    var devices = document.createTable(1, 5); row(devices.getRow(0), "设备序列号", "涉及产品型号", "在网软件版本", "升级版本信息", "备注");
                    for (var value : overview.path("deviceSummary")) {
                        var detail = detailRows.get(value.path("deviceId").asText());
                        row(devices.createRow(), value.path("serialNumber").asText(), detail == null ? "" : detail.path("productModel").asText(),
                                detail == null ? "" : detail.path("currentVersion").asText(), detail == null ? "" : detail.path("targetVersion").asText(),
                                detail == null ? "" : detail.path("remark").asText());
                    }
                } else {
                    var devices = document.createTable(1, 2); row(devices.getRow(0), "设备序列号", "设备类型");
                    for (var value : overview.path("deviceSummary")) row(devices.createRow(), value.path("serialNumber").asText(), value.path("deviceTypeCode").asText());
                }
                Map<String, String> labels = new LinkedHashMap<>();
                labels.put("preTopologyFile", "割接前拓扑"); labels.put("postTopologyFile", "割接后拓扑"); labels.put("networkConfigurationFile", "网络配置");
                for (var field : labels.entrySet()) {
                    var file = overview.path(field.getKey());
                    if (file.isNull() || file.isMissingNode()) { paragraph(document, field.getValue() + "：尚未提供", false); continue; }
                    Attachment attachment = context.attachment().apply(file);
                    var caption = paragraph(document, field.getValue() + "：" + attachment.name() + "（V" + file.path("versionNo").asInt() + "）", false);
                    if (Set.of("image/png", "image/jpeg").contains(attachment.mediaType())) { caption.setKeepNext(true); picture(document, attachment); }
                }
            }
            Map<String, String> names = Map.of("PRE_OPERATION", "割接前准备", "OPERATION", "操作步骤", "CLOSING_COLLECTION", "收尾采集",
                    "POST_BUSINESS_TEST", "业务测试", "ROLLBACK", "回退步骤", "POST_CUTOVER_SUPPORT", "割接保障");
            Map<String, List<JsonNode>> sectionRows = new LinkedHashMap<>();
            content.path("steps").forEach(step -> sectionRows.computeIfAbsent(step.path("sectionCode").asText(), ignored -> new ArrayList<>()).add(step));
            for (var section : sectionRows.entrySet()) {
                paragraph(document, names.getOrDefault(section.getKey(), section.getKey()), true);
                if ("ROLLBACK".equals(section.getKey()) && !content.path("rollbackDescription").asText().isBlank()) {
                    paragraph(document, "回退方案说明：" + content.path("rollbackDescription").asText(), false);
                }
                if (section.getValue().stream().noneMatch(step -> step.has("details"))) {
                    section.getValue().forEach(step -> paragraph(document, step.path("stepNo").asText() + "．" + step.path("content").asText(), false));
                    continue;
                }
                var columns = columns(section.getKey());
                var table = document.createTable(1, columns.size() + 1);
                var headings = new ArrayList<String>(); headings.add("序号");
                columns.forEach(column -> headings.add(column.label()));
                row(table.getRow(0), headings.toArray(String[]::new));
                for (var step : section.getValue()) {
                    var values = new ArrayList<String>(); values.add(step.path("stepNo").asText());
                    columns.forEach(column -> values.add("content".equals(column.field()) ? step.path("content").asText()
                            : step.path("details").path(column.field()).asText()));
                    row(table.createRow(), values.toArray(String[]::new));
                }
            }
            if (content.has("riskMitigations")) {
                paragraph(document, "风险缓解措施", true);
                for (var risk : content.path("riskMitigations")) {
                    var fact = risk.path("riskFact");
                    String description = context.riskDescription() == null ? fact.path("factDescription").asText()
                            : context.riskDescription().apply(fact);
                    paragraph(document, fact.path("itemName").asText() + "：" + description, false);
                    paragraph(document, "缓解措施：" + risk.path("mitigation").asText(), false);
                }
            }
            if (!supports.isEmpty()) {
                paragraph(document, "保障安排", true);
                var table = document.createTable(1, 5); row(table.getRow(0), "角色", "人员", "职责", "电话", "到场时间");
                Map<String, String> roles = Map.of("CUSTOMER", "客户", "DP_FIRST_LINE", "一线", "DP_SECOND_LINE", "二线", "DP_RND", "研发");
                for (var support : supports) row(table.createRow(), roles.getOrDefault(support.getRoleCode(), support.getRoleCode()),
                        support.getPersonName(), support.getDutyDescription(), support.getPhone(), timestamp(support.getArrivalTime()));
            }
            document.write(output); return output.toByteArray();
        } catch (java.io.IOException failure) { throw new IllegalStateException("方案文档生成失败", failure); }
    }
    private static void picture(XWPFDocument document, Attachment attachment) throws java.io.IOException {
        byte[] bytes = attachment.content();
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("方案图片内容不可用");
        try (var input = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("方案图片无法解析");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                boolean png = "image/png".equals(attachment.mediaType());
                if (!(png ? "png".equalsIgnoreCase(format) : "jpeg".equalsIgnoreCase(format))) throw new IllegalArgumentException("方案图片格式与文件类型不一致");
                double width = reader.getWidth(0) * 0.75, height = reader.getHeight(0) * 0.75;
                if (width <= 0 || height <= 0) throw new IllegalArgumentException("方案图片尺寸无效");
                double scale = Math.min(1, Math.min(450 / width, 580 / height));
                var paragraph = document.createParagraph(); paragraph.setAlignment(ParagraphAlignment.CENTER);
                paragraph.setSpacingAfter(120);
                paragraph.createRun().addPicture(new ByteArrayInputStream(bytes), png ? org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG
                        : org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG, attachment.name(),
                        org.apache.poi.util.Units.toEMU(width * scale), org.apache.poi.util.Units.toEMU(height * scale));
            } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) { throw new IllegalArgumentException("方案图片无法内嵌", ex); }
            finally { reader.dispose(); }
        }
    }
    private static List<Column> columns(String section) {
        return switch (section) {
            case "OPERATION" -> List.of(new Column("割接步骤", "stepName"), new Column("操作内容", "content"),
                    new Column("业务影响时间", "businessImpactTime"), new Column("实施人员", "operator"), new Column("操作时间", "operationTime"));
            case "ROLLBACK" -> List.of(new Column("回退步骤", "stepName"), new Column("操作内容", "content"),
                    new Column("实施人员", "operator"), new Column("操作时间", "operationTime"));
            case "POST_CUTOVER_SUPPORT" -> List.of(new Column("责任人", "operator"), new Column("时间", "content"));
            default -> List.of(new Column("操作项目", "content"), new Column("测试结果", "testResult"), new Column("责任人", "operator"));
        };
    }
    private record Column(String label, String field) {}
    private static String timestamp(Long value) {
        return value == null || value <= 0 ? "尚未填写" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()));
    }
    private static String timestamp(LocalDateTime value) {
        return value == null ? "尚未填写" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    private static XWPFParagraph paragraph(XWPFDocument document, String value, boolean heading) {
        var paragraph = document.createParagraph(); paragraph.setSpacingAfter(heading ? 120 : 80);
        var run = paragraph.createRun(); run.setFontFamily("宋体"); run.setFontSize(heading ? 15 : 11); run.setBold(heading);
        writeText(run, value);
        return paragraph;
    }
    private static void writeText(XWPFRun run, String value) {
        String[] lines = Objects.toString(value, "").split("\\R", -1);
        for (int index = 0; index < lines.length; index++) { if (index > 0) run.addBreak(); run.setText(lines[index]); }
    }
    private static void row(XWPFTableRow row, String... values) {
        for (int index = 0; index < values.length; index++) {
            var run = row.getCell(index).getParagraphs().getFirst().createRun();
            run.setFontFamily("宋体"); run.setFontSize(11); writeText(run, values[index]);
        }
    }
}
