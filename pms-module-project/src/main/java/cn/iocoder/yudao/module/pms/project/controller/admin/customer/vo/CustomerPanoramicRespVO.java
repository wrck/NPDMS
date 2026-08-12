package cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo;

import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台 - 客户资产全景 Response VO。
 * <p>
 * 对应 FR-PROJ-005：汇聚客户单位、联系人、项目、设备等关键信息，支持服务经理从客户维度查看全部服务关系。
 * V1 仅聚合客户自身、联系人、项目统计；设备、服务、巡检等跨模块数据由工程域、资产域、服务域提供后增量补齐。
 */
@Schema(description = "管理后台 - 客户资产全景 Response VO")
@Data
public class CustomerPanoramicRespVO {

    @Schema(description = "客户编号", example = "1024")
    private Long id;

    @Schema(description = "客户编码", example = "C20260101001")
    private String code;

    @Schema(description = "客户名称", example = "上海某某有限公司")
    private String name;

    @Schema(description = "客户简称", example = "某某")
    private String shortName;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "地址", example = "上海市浦东新区张江高科技园区")
    private String address;

    @Schema(description = "备注", example = "VIP 客户")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "联系人列表（已脱敏）")
    private List<CustomerContactRespVO> contacts = Collections.emptyList();

    @Schema(description = "主联系人姓名", example = "张三")
    private String primaryContactName;

    @Schema(description = "主联系人手机（已脱敏）", example = "138****5678")
    private String primaryContactMobileMasked;

    @Schema(description = "关联项目总数", example = "5")
    private Integer projectCount;

    @Schema(description = "在执行项目数", example = "2")
    private Integer activeProjectCount;

    @Schema(description = "关联设备总数（由资产域后续补齐，V1 返回 0）", example = "0")
    private Integer assetCount;
}
