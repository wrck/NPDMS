package cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class DepartmentOptionsReqVO {
    @Schema(description = "编码前缀并集，可带末尾*；未指定返回全部启用部门", example = "16*,31*")
    private List<String> codePrefixes;
}
