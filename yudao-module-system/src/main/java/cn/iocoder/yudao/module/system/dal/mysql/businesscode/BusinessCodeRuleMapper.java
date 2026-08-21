package cn.iocoder.yudao.module.system.dal.mysql.businesscode;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.businesscode.BusinessCodeRuleDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessCodeRuleMapper extends BaseMapperX<BusinessCodeRuleDO> {

    default List<BusinessCodeRuleDO> selectActiveForUpdate(long tenantId, String ruleCode) {
        LocalDateTime now = LocalDateTime.now();
        return selectList(new LambdaQueryWrapper<BusinessCodeRuleDO>()
                .eq(BusinessCodeRuleDO::getTenantId, tenantId)
                .eq(BusinessCodeRuleDO::getRuleCode, ruleCode)
                .eq(BusinessCodeRuleDO::getStatus, "ACTIVE")
                .le(BusinessCodeRuleDO::getEffectiveFrom, now)
                .and(query -> query.isNull(BusinessCodeRuleDO::getEffectiveTo)
                        .or().gt(BusinessCodeRuleDO::getEffectiveTo, now))
                .last("FOR UPDATE"));
    }

    @Update("""
            UPDATE plt_business_code_rule
            SET next_value = #{nextValue}, version = version + 1, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND next_value = #{expectedValue}
            """)
    int advance(long id, long expectedValue, long nextValue);
}
