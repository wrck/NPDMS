package cn.iocoder.yudao.module.pms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalFieldPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批敏感字段权限 Mapper。
 */
@Mapper
public interface ApprovalFieldPermissionMapper extends BaseMapper<ApprovalFieldPermission> {
}
