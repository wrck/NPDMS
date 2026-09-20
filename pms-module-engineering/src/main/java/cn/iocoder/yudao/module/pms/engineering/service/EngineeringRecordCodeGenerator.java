package cn.iocoder.yudao.module.pms.engineering.service;

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
 * 工程实施域操作记录编码生成器。
 * <p>
 * 安装编码、配置编码等无业务含义编码统一按 "{项目编码}-{类型码}-三位序号" 由系统生成，
 * 作为系统内部关联，不开放手工填写。
 */
@Component
public class EngineeringRecordCodeGenerator {

    /** 类型码：工勘 */
    public static final String SITE_SURVEY = "GK";
    /** 类型码：需求 */
    public static final String REQUIREMENT = "XQ";
    /** 类型码：工程交底 */
    public static final String BRIEFING = "JD";
    /** 类型码：资源就绪 */
    public static final String RESOURCE = "ZY";
    /** 类型码：实施方案 */
    public static final String SOLUTION = "FA";
    /** 类型码：到货签收 */
    public static final String ARRIVAL = "QS";
    /** 类型码：硬件安装 */
    public static final String INSTALLATION = "AZ";
    /** 类型码：配置调试 */
    public static final String CONFIGURATION = "PZ";
    /** 类型码：业务联调 */
    public static final String JOINT_TEST = "LT";
    /** 类型码：现场培训 */
    public static final String TRAINING = "PX";
    /** 类型码：交付件 */
    public static final String DELIVERABLE = "JF";
    /** 类型码：实施问题 */
    public static final String ISSUE = "WT";

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
