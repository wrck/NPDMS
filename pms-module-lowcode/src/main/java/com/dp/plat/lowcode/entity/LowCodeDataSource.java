package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多数据源配置实体（批次3-T7）。
 *
 * <p>统一建模三种外部数据源集成模式：</p>
 * <ul>
 *   <li>DIRECT（外联库直连）：直接通过 JDBC 连接外部数据库，所有读写操作直接透传到外部库</li>
 *   <li>REPLICA（外联表副本）：从外部数据库同步表结构+数据到本地，读操作走本地副本，写操作回写到外部库</li>
 *   <li>FEDERATED（中间库整合）：通过中间库（如 MySQL FEDERATED 引擎或 JDBC 桥接）整合多个外部数据源</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_datasource")
public class LowCodeDataSource extends BaseEntity {

    /** 数据源编码（唯一） */
    private String code;

    /** 数据源名称 */
    private String name;

    /** 数据库类型：mysql / postgresql / sqlserver / oracle */
    private String dbType;

    /** 集成模式：DIRECT / REPLICA / FEDERATED */
    private String integrationMode = "DIRECT";

    /** JDBC URL */
    private String url;

    /** 用户名 */
    private String username;

    /** 密码（加密存储） */
    private String password;

    /** 驱动类名 */
    private String driverClassName;

    /** 连接池大小 */
    private Integer poolSize = 5;

    /** 状态：ACTIVE / INACTIVE */
    private String status = "ACTIVE";

    /** 关联的实体编码（REPLICA 模式：同步到本地的实体 code） */
    private String linkedEntityCode;

    /** 同步配置 JSON（REPLICA 模式：同步频率/字段映射/过滤条件） */
    private String syncConfig;

    /** 描述 */
    private String description;
}
