package cn.iocoder.yudao.module.system.dal.mysql.company;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompanyMapper extends BaseMapperX<CompanyDO> {

    default CompanyDO selectByCode(String code) {
        return selectOne(CompanyDO::getCode, code);
    }

}
