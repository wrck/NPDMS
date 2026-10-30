package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
@ExcelIgnoreUnannotated
public class ProjectEquipmentExportRow {
    @ExcelProperty("序号") private Integer no;
    @ExcelProperty("产品编码") private String productCode;
    @ExcelProperty("产品型号") private String productModel;
    @ExcelProperty("产品名称") private String productName;
    @ExcelProperty("订单号") private String orderNo;
    @ExcelProperty("项目数量") private BigDecimal projectQty;
    @ExcelProperty("已发货数量") private BigDecimal deliveredQty;
    @ExcelProperty("未发货数量") private BigDecimal openQty;
}
