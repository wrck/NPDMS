package cn.iocoder.yudao.module.pms.acceptance.service;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectScopedCodes;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * 验收交维域操作记录编码生成器。
 * <p>
 * 验收编号、交付件编号、完工证明编号、归档文档编号等无业务含义编码统一按
 * "{项目编码}-{类型码}-三位序号" 由系统生成，作为系统内部关联，不开放手工填写。
 */
@Component
public class AcceptanceRecordCodeGenerator {

    /** 类型码：验收记录 */
    public static final String ACCEPTANCE = "YS";
    /** 类型码：交付件检查 */
    public static final String DELIVERABLE_CHECKLIST = "JC";
    /** 类型码：完工证明 */
    public static final String COMPLETION_CERTIFICATE = "WZ";
    /** 类型码：归档文档 */
    public static final String ARCHIVE_DOCUMENT = "GD";

    @Resource
    private ProjectCodeQueryApi projectCodeQueryApi;

    /**
     * 生成项目内唯一的下一条记录编码。
     *
     * @param projectId       项目编号
     * @param typeCode        记录类型码
     * @param mapper          记录表 Mapper
     * @param projectIdGetter 项目编号字段
     * @param codeGetter      编码字段
     * @param <T>             记录 DO 类型
     * @return "{项目编码}-{类型码}-###" 编码
     */
    public <T> String next(Long projectId, String typeCode, BaseMapperX<T> mapper,
                           SFunction<T, Long> projectIdGetter, SFunction<T, String> codeGetter) {
        String projectCode = projectCodeQueryApi.getProjectCode(projectId);
        List<String> existing = mapper.selectList(new LambdaQueryWrapperX<T>().eq(projectIdGetter, projectId))
                .stream().map(codeGetter).filter(Objects::nonNull).toList();
        return ProjectScopedCodes.next(projectCode, typeCode, existing);
    }
}
