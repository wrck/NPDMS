package cn.iocoder.yudao.module.pms.platform.support.entity;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;

/**
 * 统一业务实体基类：共同身份、租户、审计、逻辑删除与乐观锁并发依据只在此定义一次。
 * 业务子类不得遮蔽同名基础字段；不同业务保留自身状态集合。
 * 修订实体继承对应业务实体字段并另行实现 EntityRevision，本基类不承载修订语义。
 */
public abstract class BaseBusinessEntity extends TenantBaseDO {

    /**
     * 原生业务主键（列 id）。跨前端传输保持 Long 精度，由统一序列化保证。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 乐观锁并发依据（列 version）。未启用并发控制的遗留表在迁移批次中补列或显式声明豁免。
     */
    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
