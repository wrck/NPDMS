package cn.iocoder.yudao.module.system.service.permission;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
/** Association persistence and cleanup only; does not depend on role/user/menu authorization services. */
@Service @RequiredArgsConstructor
public class PermissionAssociationService {
 private final RoleMenuMapper roleMenuMapper;
 private final UserRoleMapper userRoleMapper;
 @Transactional(rollbackFor=Exception.class)
 @Caching(evict={@CacheEvict(value=RedisKeyConstants.MENU_ROLE_ID_LIST,allEntries=true),
                 @CacheEvict(value=RedisKeyConstants.USER_ROLE_ID_LIST,allEntries=true)})
 public void processRoleDeleted(Long roleId){userRoleMapper.deleteListByRoleId(roleId);roleMenuMapper.deleteListByRoleId(roleId);}
 @CacheEvict(value=RedisKeyConstants.MENU_ROLE_ID_LIST,key="#menuId")
 public void processMenuDeleted(Long menuId){roleMenuMapper.deleteListByMenuId(menuId);}
 @CacheEvict(value=RedisKeyConstants.USER_ROLE_ID_LIST,key="#userId")
 public void processUserDeleted(Long userId){userRoleMapper.deleteListByUserId(userId);}
 public Set<Long> getUserRoleIdListByRoleId(Collection<Long> roleIds){return convertSet(userRoleMapper.selectListByRoleIds(roleIds),UserRoleDO::getUserId);}
}
