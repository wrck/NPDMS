package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.AddressPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AddressDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

@Mapper
public interface AddressMapper extends BaseMapperX<AddressDO> {

    default PageResult<AddressDO> selectPage(AddressPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AddressDO>()
                .likeIfPresent(AddressDO::getFullAddress, reqVO.getFullAddress())
                .eqIfPresent(AddressDO::getDistrictCode, reqVO.getDistrictCode())
                .eqIfPresent(AddressDO::getStatus, reqVO.getStatus())
                .orderByDesc(AddressDO::getId));
    }

    default List<AddressDO> selectListByFingerprint(String fingerprint) {
        return selectList(AddressDO::getAddressFingerprint, fingerprint);
    }

    default int updateByIdAndVersion(AddressDO update, Integer expectedVersion) {
        return update(update, new LambdaUpdateWrapper<AddressDO>()
                .eq(AddressDO::getId, update.getId())
                .eq(AddressDO::getVersion, expectedVersion));
    }
}
