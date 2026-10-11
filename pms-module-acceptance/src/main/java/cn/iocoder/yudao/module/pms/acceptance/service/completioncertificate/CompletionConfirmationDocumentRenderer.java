package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.CompletionSatisfactionBundleService.CertificateSnapshot;
import org.springframework.web.util.HtmlUtils;
import java.util.*;
/** Immutable confirmation documents use the same frozen facts shown to the customer. */
public final class CompletionConfirmationDocumentRenderer {
    private CompletionConfirmationDocumentRenderer(){}
    public static byte[] render(String kind,CertificateSnapshot proof,String questions,String answerSnapshot,String image,java.time.LocalDateTime signedAt){
        var html=new StringBuilder("<!DOCTYPE html><html lang=\"zh-CN\"><meta charset=\"UTF-8\"><style>body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:32px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #cbd5e1;padding:12px;text-align:left}th{background:#f3f6fa}img{max-width:100%;max-height:160px}</style><body><h1>");
        html.append("COMPLETION".equals(kind)?"完工证明":"满意度调查表").append("</h1><table>");
        row(html,"证明名称",proof.name());row(html,"证明编号",proof.certificateNo()==null?proof.code():proof.certificateNo());row(html,"用户单位",proof.customerUnit());
        if("COMPLETION".equals(kind)){
            row(html,"合同号",proof.contractNo());row(html,"工程服务类型",proof.serviceType());row(html,"工程师",proof.engineerName());row(html,"联系方式",proof.engineerContact());
            row(html,"完成到货验收",proof.itemArrival());row(html,"完成安装调试",proof.itemInstall());row(html,"完成业务上线/割接",proof.itemCutover());row(html,"完成现场培训",proof.itemTraining());row(html,"工程文档已移交",proof.itemDocs());row(html,"完工日期",proof.completionDate()==null?null:proof.completionDate().toString());
            for(var device:proof.devices())row(html,"设备",device.deviceType()+" · "+device.deviceModel()+" × "+device.quantity());
        }else{
            var submitted=new HashMap<String,tools.jackson.databind.JsonNode>();
            var answers=JsonUtils.parseTree(answerSnapshot).path("answers");
            for(var answer:answers)submitted.put(answer.path("questionCode").asText(),answer.get("value"));
            html.append("</table><p>尊敬的客户：非常感谢您对本次工程实施的大力支持！请您对本次工程实施情况给予评价，谢谢。</p><table><thead><tr><th>评价类别</th><th>调查项目</th>");
            var rating=new ArrayList<tools.jackson.databind.JsonNode>();var other=new ArrayList<tools.jackson.databind.JsonNode>();
            var columns=new LinkedHashMap<String,String>();
            for(var question:JsonUtils.parseTree(questions).path("questions")){
                if("RATING".equals(question.path("type").asText())){rating.add(question);for(var option:question.path("options"))columns.putIfAbsent(optionKey(option),optionLabel(option));}
                else other.add(question);
            }
            for(String label:columns.values())html.append("<th>").append(HtmlUtils.htmlEscape(label)).append("</th>");
            html.append("<th>备注</th></tr></thead><tbody>");
            for(int index=0;index<rating.size();index++){
                var question=rating.get(index);String category=category(question.path("title").asText());html.append("<tr>");
                if(index==0 || !category.equals(category(rating.get(index-1).path("title").asText()))){
                    int end=index+1;while(end<rating.size() && category.equals(category(rating.get(end).path("title").asText())))end++;
                    html.append("<th rowspan=\"").append(end-index).append("\">").append(category).append("</th>");
                }
                html.append("<th>").append(HtmlUtils.htmlEscape(question.path("title").asText())).append("</th>");
                var value=submitted.get(question.path("code").asText());
                for(String column:columns.keySet()){
                    String mark="—";for(var option:question.path("options"))if(column.equals(optionKey(option)))mark=value!=null && option.path("code").asText().equals(value.asText())?"✓":"";
                    html.append("<td style=\"text-align:center\">").append(mark).append("</td>");
                }
                html.append("<td>—</td></tr>");
            }
            for(var question:other){
                var value=submitted.get(question.path("code").asText());var codes=new ArrayList<String>();
                if(value!=null){if(value.isArray())for(var selected:value)codes.add(selected.asText());else codes.add(value.asText());}
                var labels=new ArrayList<String>();for(String code:codes){String label=code;for(var option:question.path("options"))if(code.equals(option.path("code").asText()))label=option.path("label").asText();labels.add(label);}
                html.append("<tr><th colspan=\"2\">").append(HtmlUtils.htmlEscape(question.path("title").asText())).append("</th><td colspan=\"").append(columns.size()+1).append("\" style=\"white-space:pre-wrap\">").append(HtmlUtils.htmlEscape(String.join("、",labels))).append("</td></tr>");
            }
            html.append("</tbody>");
        }
        html.append("</table><h2>客户手写签字（同时确认两份表单）</h2>");
        if(image!=null)html.append("<img alt=\"客户手写签字\" src=\"").append(HtmlUtils.htmlEscape(image)).append("\">");
        html.append("<p>确认时间：").append(signedAt).append("</p></body></html>");return html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void row(StringBuilder html,String label,String value){html.append("<tr><th>").append(HtmlUtils.htmlEscape(label)).append("</th><td>").append(HtmlUtils.htmlEscape(value==null?"":value)).append("</td></tr>");}
    private static String optionLabel(tools.jackson.databind.JsonNode option){
        String score=option.has("score")?new java.math.BigDecimal(option.path("score").asText()).stripTrailingZeros().toPlainString():"";
        return option.path("label").asText()+score;
    }
    private static String optionKey(tools.jackson.databind.JsonNode option){return optionLabel(option);}
    private static String category(String title){
        return switch(title){case "总体工程质量","设备安装规范","工程交付及时性"->"工程质量";
            case "功能完善性","稳定、可靠性"->"产品质量";
            case "实施人员服务意识及配合度","问题响应及时性","实施人员技术水平","工程培训讲解效果","工程文档完整性"->"服务质量";default->"—";};
    }
}
