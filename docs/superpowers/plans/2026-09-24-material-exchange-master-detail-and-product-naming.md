# 换货申请主子表重构与命名对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 换货申请主表物料组改订单行多值编码、子表每行换货产品选择（产品信息下拉），全链命名对齐 plain 产品组/设备组。

**Architecture:** V356 迁移完成更名与加宽；资产域新增产品信息只读域（page API + 跨模块快照 API）；工程域保存联动（拼接/退出/校验/快照）；前端行内下拉与展示对齐；V357 种子覆盖组合场景。

**Tech Stack:** JDK 25 / Maven / MyBatis-Plus / MySQL 8 / Vue3 + element-plus / Flyway

**依据设计稿:** `docs/superpowers/specs/2026-09-24-material-exchange-master-detail-and-product-naming-design.md`（提交 cb8cb4469）

**执行注意（每任务开工前核对）:**
- 并行工作流可能新增迁移：创建迁移前重查 `sql/migrations/` 最大版本号，取下一个空闲版本（本计划按 V356/V357 编写，被占用则顺延并同步本计划与提交说明）
- 浏览器验证地址一律 `10.210.0.11:19191`，MySQL 容器名一律 `npdms-domain-test-mysql-1`（库 `npdms_domain_test`，root 密码见 `.env` NPDMS_MYSQL_ROOT_PASSWORD）
- 只提交本次改动文件；并行新文件不代提交；不自动推送

---

### Task 1: V356 DDL 迁移（更名+加宽+新增）

**Files:**
- Create: `sql/migrations/V356__material_exchange_product_naming.sql`

- [ ] **Step 1: 确认迁移版本空闲**

Run: `ls sql/migrations/ | grep -oE "^V[0-9]+" | sort -V | tail -1`
Expected: V355（被占用则本文件改为下一个空闲版本号，下同）

- [ ] **Step 2: 写迁移文件**

```sql
-- 换货申请主子表命名对齐：plain 产品组/设备组；历史数据随更名保留
-- 主表：物料组更名产品组，编码多值加宽，名称/型号退出置 NULL（改可空）
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN material_code TO product_code;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_code varchar(500) NULL COMMENT '产品编码（订单行去重拼接）';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN material_name TO product_name;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_name varchar(200) NULL COMMENT '产品名称';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN specification TO product_model;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_model varchar(200) NULL COMMENT '产品型号';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN equipment_id TO device_id;
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN new_equipment_id TO new_device_id;

-- 子表：name/equipment_id 更名，快照列 COMMENT 对齐，新增 product_id 引用
ALTER TABLE imp_eng_material_exchange_serial
  RENAME COLUMN name TO product_name;
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_name varchar(255) NULL COMMENT '换货产品名称（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  RENAME COLUMN equipment_id TO device_id;
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_code varchar(128) NULL COMMENT '换货产品编码（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_model varchar(255) NULL COMMENT '换货产品型号（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  ADD COLUMN product_id bigint NULL COMMENT '换货产品ID（产品信息引用）' AFTER device_id;
```

- [ ] **Step 3: 后端启动前本地预校验（Flyway 会随启动应用；本任务先只做 SQL 静态核对）**

Run: `git add sql/migrations/V356__material_exchange_product_naming.sql`
Expected: 无输出即成功

- [ ] **Step 4: 提交**

```bash
git commit -m "feat(pms): 换货申请主子表命名对齐迁移（V356）"
```

---

### Task 2: 资产域产品信息只读域（DO/Mapper/Service/Controller）

**Files:**
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/product/AssetProductOfficialDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/product/AssetProductOfficialMapper.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/product/query/ProductOfficialPageQuery.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/product/AssetProductOfficialController.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/product/vo/ProductOfficialPageReqVO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/product/vo/ProductOfficialRespVO.java`

- [ ] **Step 1: DO（@TableId ASSIGN_ID，表无自增主键）**

```java
package cn.iocoder.yudao.module.pms.asset.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 产品信息只读副本：集成同步写入（generic-targets），资产域只读。 */
@TableName("ast_product_official_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductOfficialDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String productCode;
    private String productName;
    private String productModel;
    private String productDesc;
    private String technicalSpec;
    private String status;
}
```

- [ ] **Step 2: 场景化 Query + Mapper（单参 Query 对象；简单单表条件 LambdaQueryWrapperX）**

```java
package cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query;

import lombok.Data;

/** 产品信息分页场景化查询：关键字跨名称/编码/型号模糊，停用行仅返回 status 供前端禁选。 */
@Data
public class ProductOfficialPageQuery {

    private Long pageNo;
    private Long pageSize;
    private String keyword;
}
```

```java
package cn.iocoder.yudao.module.pms.asset.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductOfficialPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetProductOfficialMapper extends BaseMapperX<AssetProductOfficialDO> {

    default PageResult<AssetProductOfficialDO> selectPage(ProductOfficialPageQuery query) {
        return selectPage(query.getPageNo(), query.getPageSize(),
                new LambdaQueryWrapperX<AssetProductOfficialDO>()
                        .and(keyword(query.getKeyword()).getExpression() != null
                                ? keyword(query.getKeyword()).getExpression()::apply : null)
                        .orderByDesc(AssetProductOfficialDO::getId));
    }
}
```

注意：上写法绕——按仓库既有分页 Query 模式改为 Service 拼装 wrapper（见 Step 3），Mapper 只留 `selectPage(pageNo, pageSize, wrapper)` 继承方法。**Mapper 最终形态：**

```java
package cn.iocoder.yudao.module.pms.asset.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetProductOfficialMapper extends BaseMapperX<AssetProductOfficialDO> {
}
```

- [ ] **Step 3: Controller + VO（分页 + 关键字三字段模糊；`/api/v1/pms/...` 规范）**

```java
package cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo;

import lombok.Data;

@Data
public class ProductOfficialPageReqVO {

    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private String keyword;
}
```

```java
package cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo;

import lombok.Data;

@Data
public class ProductOfficialRespVO {

    private Long id;
    private String productCode;
    private String productName;
    private String productModel;
    private String status;
}
```

```java
package cn.iocoder.yudao.module.pms.asset.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo.ProductOfficialPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo.ProductOfficialRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/asset-product-officials")
@Validated
public class AssetProductOfficialController {

    @Resource
    private AssetProductOfficialMapper productOfficialMapper;

    @GetMapping("/page")
    public CommonResult<PageResult<ProductOfficialRespVO>> page(ProductOfficialPageReqVO reqVO) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(reqVO.getPageNo());
        pageParam.setPageSize(Math.min(reqVO.getPageSize(), 100L));
        LambdaQueryWrapperX<AssetProductOfficialDO> wrapper = new LambdaQueryWrapperX<AssetProductOfficialDO>()
                .likeIf(AssetProductOfficialDO::getProductName, reqVO.getKeyword())
                .or().likeIf(AssetProductOfficialDO::getProductCode, reqVO.getKeyword())
                .or().likeIf(AssetProductOfficialDO::getProductModel, reqVO.getKeyword());
        if (StringUtils.isBlank(reqVO.getKeyword())) {
            wrapper = new LambdaQueryWrapperX<AssetProductOfficialDO>()
                    .orderByDesc(AssetProductOfficialDO::getId);
        }
        wrapper.orderByDesc(AssetProductOfficialDO::getId);
        PageResult<AssetProductOfficialDO> page = productOfficialMapper
                .selectPage(pageParam, wrapper);
        return success(new PageResult<>(productOfficialMapper.selectList(wrapper).stream() // 占位防误用，见下
                .skip((pageParam.getPageNo() - 1) * pageParam.getPageSize()).limit(pageParam.getPageSize()).toList(), page.getTotal()));
    }
}
```

**最终形态修正**：PageResult 分页必须走 mapper.selectPage(pageParam, wrapper)，不得手工 skip/limit。**Controller page 方法最终代码：**

```java
    @GetMapping("/page")
    public CommonResult<PageResult<ProductOfficialRespVO>> page(ProductOfficialPageReqVO reqVO) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(reqVO.getPageNo());
        pageParam.setPageSize(Math.min(reqVO.getPageSize(), 100L));
        LambdaQueryWrapperX<AssetProductOfficialDO> wrapper = new LambdaQueryWrapperX<AssetProductOfficialDO>()
                .orderByDesc(AssetProductOfficialDO::getId);
        if (StringUtils.isNotBlank(reqVO.getKeyword())) {
            wrapper.and(w -> w.like(AssetProductOfficialDO::getProductName, reqVO.getKeyword())
                    .or().like(AssetProductOfficialDO::getProductCode, reqVO.getKeyword())
                    .or().like(AssetProductOfficialDO::getProductModel, reqVO.getKeyword()));
        }
        PageResult<AssetProductOfficialDO> page = productOfficialMapper.selectPage(pageParam, wrapper);
        PageResult<ProductOfficialRespVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(BeanUtils.toBean(page.getList(), ProductOfficialRespVO.class));
        return success(result);
    }
```

（`BeanUtils` = `cn.iocoder.yudao.framework.common.util.object.BeanUtils`；`Mapper` 仅需继承 `BaseMapperX`，`selectPage(PageParam, Wrapper)` 为其继承方法。）

- [ ] **Step 4: 编译**

Run: `cd /e/AICoding/Projects/NPDMS && mvn -q compile -pl pms-module-asset -am -o`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/product/ pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/product/ pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/product/
git commit -m "feat(asset): 新增产品信息只读分页 API（/api/v1/pms/asset-product-officials）"
```

---

### Task 3: 资产域产品快照跨模块 API（asset-api + impl）

**Files:**
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/ProductOfficialSnapshot.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/AssetProductOfficialApi.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/AssetProductOfficialApiImpl.java`

- [ ] **Step 1: api DTO + 接口（仅返回 ACTIVE 快照；空入参返回空结果）**

```java
package cn.iocoder.yudao.module.pms.asset.api.product;

/** 产品信息 ACTIVE 快照：工程域按引用写入，不信任前端传值。 */
public record ProductOfficialSnapshot(Long id, String productCode, String productName,
                                      String productModel) {
}
```

```java
package cn.iocoder.yudao.module.pms.asset.api.product;

import java.util.Collection;
import java.util.List;

/** 产品信息只读事实 API：换货产品引用校验与快照解析。 */
public interface AssetProductOfficialApi {

    /**
     * 批量解析 ACTIVE 产品快照；id 不存在或非 ACTIVE 不返回（调用方按缺失抛错）。
     * 空入参返回空结果。
     */
    List<ProductOfficialSnapshot> getActiveProductSnapshots(Collection<Long> productIds);
}
```

- [ ] **Step 2: impl（selectList 批查 + status=ACTIVE 过滤）**

```java
package cn.iocoder.yudao.module.pms.asset.api.product;

import cn.iocoder.yudao.module.pms.asset.api.product.ProductOfficialSnapshot;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

@Component
@Validated
public class AssetProductOfficialApiImpl implements AssetProductOfficialApi {

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Resource
    private AssetProductOfficialMapper productOfficialMapper;

    @Override
    public List<ProductOfficialSnapshot> getActiveProductSnapshots(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productOfficialMapper.selectList(new LambdaQueryWrapperX<AssetProductOfficialDO>()
                        .in(AssetProductOfficialDO::getId, productIds)
                        .eq(AssetProductOfficialDO::getStatus, STATUS_ACTIVE)).stream()
                .map(row -> new ProductOfficialSnapshot(row.getId(), row.getProductCode(),
                        row.getProductName(), row.getProductModel()))
                .toList();
    }
}
```

- [ ] **Step 3: 失败测试（先写）**

Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/product/AssetProductOfficialApiImplTest.java`

```java
package cn.iocoder.yudao.module.pms.asset.api.product;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetProductOfficialApiImplTest {

    private AssetProductOfficialMapper mapper;
    private AssetProductOfficialApiImpl api;

    @BeforeEach
    void setUp() {
        mapper = mock(AssetProductOfficialMapper.class);
        api = new AssetProductOfficialApiImpl();
        setField(api, mapper);
    }

    private static void setField(AssetProductOfficialApiImpl api, AssetProductOfficialMapper mapper) {
        org.springframework.test.util.ReflectionTestUtils.setField(api, "productOfficialMapper", mapper);
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(api.getActiveProductSnapshots(List.of()).isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void nullInputReturnsEmpty() {
        assertTrue(api.getActiveProductSnapshots(null).isEmpty());
    }

    @Test
    void activeOnlySnapshots() {
        AssetProductOfficialDO active = new AssetProductOfficialDO();
        active.setId(11L);
        active.setProductCode("01100003");
        active.setProductName("DPtech IPS2000-MA-N");
        active.setProductModel("IPS2000-MA-N+1Y");
        active.setStatus("ACTIVE");
        AssetProductOfficialDO published = new AssetProductOfficialDO();
        published.setId(12L);
        published.setStatus("FAST001_TEST_PUBLISHED");
        when(mapper.selectList(any())).thenReturn(List.of(active, published));

        List<ProductOfficialSnapshot> snapshots = api.getActiveProductSnapshots(List.of(11L, 12L));

        assertEquals(1, snapshots.size());
        assertEquals(11L, snapshots.get(0).id());
        assertEquals("01100003", snapshots.get(0).productCode());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AssetProductOfficialDO>> captor =
                forClassWrapper();
        verify(mapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    private static ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AssetProductOfficialDO>> forClassWrapper() {
        return ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
    }
}
```

注意：`getSqlSegment().contains("status")` 断言 status 过滤真实生效（AST 段）——若 MyBatis-Plus Wrapper 在 mock 环境下 SqlSegment 不含 status，改为仅断言返回集合只含 ACTIVE 行并删去 captor 块。

- [ ] **Step 4: 跑测试确认绿**

Run: `mvn -q test -pl pms-module-asset -o -Dtest=AssetProductOfficialApiImplTest`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/ pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/ pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/product/
git commit -m "feat(asset-api): 产品信息 ACTIVE 快照跨模块 API 与测试"
```

---

### Task 4: 工程域字段更名（DO/VO/错误码）

**Files:**
- Modify: `pms-module-engineering/.../dal/dataobject/materialexchange/MaterialExchangeDO.java`
- Modify: `pms-module-engineering/.../dal/dataobject/materialexchange/MaterialExchangeSerialDO.java`
- Modify: `pms-module-engineering/.../controller/admin/materialexchange/vo/MaterialExchangeSaveReqVO.java`
- Modify: `pms-module-engineering/.../controller/admin/materialexchange/vo/MaterialExchangeRespVO.java`
- Modify: `pms-module-engineering/.../controller/admin/materialexchange/vo/MaterialExchangeSerialVO.java`
- Modify: `pms-module-engineering/.../enums/ErrorCodeConstants.java`

- [ ] **Step 1: 主表 DO 更名（保持 ALWAYS 策略语义）**

主表 DO（`MaterialExchangeDO.java`）逐项修改（`@Schema`/注释同步）：

| 旧 | 新 | 说明 |
|---|---|---|
| `private String materialName;` | `@com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)\n    private String productName;` | 更名 + 退出置 NULL 依赖 ALWAYS |
| `private String materialCode;` | `@com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)\n    private String productCode;` | 更名 + ALWAYS 强制回写拼接值 |
| `private String specification;` | `@com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)\n    private String productModel;` | 更名 + ALWAYS |
| `@TableField(updateStrategy = FieldStrategy.ALWAYS) private Long equipmentId;` | `private Long deviceId;`（保留 ALWAYS 注解） | 设备组更名 |
| `private Long newEquipmentId;` | `private Long newDeviceId;` | 设备组更名 |

- [ ] **Step 2: 子表 DO 更名**

子表 DO（`MaterialExchangeSerialDO.java`）逐项修改：

| 旧 | 新 |
|---|---|
| `private String name;` | `private String productName;` |
| `private Long equipmentId;` | `private Long deviceId;` |
| （无） | `private Long productId;`（新增，紧跟 deviceId） |

- [ ] **Step 3: VO 更名（SaveReqVO/RespVO/SerialVO）**

主表 VO：`materialName`→`productName`、`materialCode`→`productCode`、`specification`→`productModel`、`equipmentId`→`deviceId`、`newEquipmentId`→`newDeviceId`（@Schema 描述同步；SaveReqVO 若有原值预填逻辑同步）。
子表 VO：`name`→`productName`、`equipmentId`→`deviceId`、新增 `productId`（紧跟 deviceId）。

- [ ] **Step 4: 新错误码**

`ErrorCodeConstants.java` 在 `MATERIAL_EXCH_SCOPE_LINE_INVALID`（1_011_014_008）后追加：

```java
    ErrorCode MATERIAL_EXCH_PRODUCT_INVALID = new ErrorCode(1_011_014_009, "换货产品无效或已停用，请重新选择");
```

（创建前重查该文件 1_011_014_009 未被并行占用。）

- [ ] **Step 5: 全模块编译定位漏改引用**

Run: `mvn -q compile -pl pms-module-engineering -am -o`
Expected: BUILD SUCCESS；若报错均为本更名漏改（getMaterialName/getEquipmentId/setName 等），逐个改到新名（语义不变：`setName`（子行）→`setProductName`）

- [ ] **Step 6: 跑受影响既有测试**

Run: `mvn -q test -pl pms-module-engineering -o -Dtest=MaterialExchangeSerialServiceTest`
Expected: 12/12 绿（字段更名传递；失败处按新名修正测试）

- [ ] **Step 7: 提交**

```bash
git add pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/materialexchange/ pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/materialexchange/vo/ pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/enums/ErrorCodeConstants.java pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/materialexchange/
git commit -m "refactor(pms): 换货申请主子表字段更名产品组/设备组"
```

---

### Task 5: 工程域保存联动（拼接/退出/校验/快照）

**Files:**
- Modify: `pms-module-engineering/.../service/materialexchange/MaterialExchangeServiceImpl.java`
- Modify: `pms-module-engineering/pom.xml`（如尚无 asset-api 依赖则补；Task 0 已确认 engineering pom 已有 `pms-module-asset-api`，本任务确认无需改）
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/materialexchange/MaterialExchangeServiceTest.java`

- [ ] **Step 1: 注入产品快照 API**

`MaterialExchangeServiceImpl.java` 在 `scopeLineFactApi` 字段后追加：

```java
    @Resource
    private AssetProductOfficialApi assetProductOfficialApi;
```

import 增加 `cn.iocoder.yudao.module.pms.asset.api.product.AssetProductOfficialApi` 与 `cn.iocoder.yudao.module.pms.asset.api.product.ProductOfficialSnapshot`。

- [ ] **Step 2: ExchangeLine 增加换货产品引用并全链更名 device**

`ExchangeLine` 内部类（`MaterialExchangeServiceImpl.java:274-283`）改为：

```java
    /** 行解析结果：清单行（范围事实）与旧序列号行（设备事实）分流；显式行与已保存快照共用。 */
    private static final class ExchangeLine {
        private DeliveryScopeLineRef scopeRef;
        private Long legacyDeviceId;
        private DeliveryScopeLineFact scopeFact;
        private SelectedProjectDevice device;
        private Long exchangeProductId;

        private ExchangeLine(DeliveryScopeLineRef scopeRef, Long legacyDeviceId, Long exchangeProductId) {
            this.scopeRef = scopeRef;
            this.legacyDeviceId = legacyDeviceId;
            this.exchangeProductId = exchangeProductId;
        }
```

`resolveExplicitLines`（`:238-252`）构造点改为：

```java
            if (row.getScopeDetailId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofDetail(row.getScopeDetailId()), null,
                        row.getProductId()));
            } else if (row.getScopeId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofScope(row.getScopeId()), null,
                        row.getProductId()));
            } else if (row.getDeviceId() != null) {
                lines.add(new ExchangeLine(null, row.getDeviceId(), null));
            } else {
                throw exception(MATERIAL_EXCH_SCOPE_LINE_INVALID);
            }
```

`resolveSavedLines`（`:255-271`）构造点改为（DO 同名取值）：

```java
            if (row.getScopeDetailId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofDetail(row.getScopeDetailId()), null,
                        row.getProductId()));
            } else if (row.getScopeId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofScope(row.getScopeId()), null,
                        row.getProductId()));
            } else if (row.getDeviceId() != null) {
                lines.add(new ExchangeLine(null, row.getDeviceId(), null));
            }
        }
        if (lines.isEmpty() && existing.getDeviceId() != null) {
            lines.add(new ExchangeLine(null, existing.getDeviceId(), null));
        }
```

`resolveAndValidateLines` 内部 `line.legacyEquipmentId` 全部改为 `line.legacyDeviceId`（`:217-223`、`:232-233`）；回退入口 `request.getEquipmentId()` → `request.getDeviceId()`、`new ExchangeLine(null, request.getDeviceId(), null)`；主表原设备编号回写 `request.setEquipmentId(...)` → `request.setDeviceId(...)`（语义注释不变：清单行无单设备语义，仅旧行保留设备编号）。

- [ ] **Step 3: 拼接与退出（create/update）**

`createMaterialExchange`（`:125-139`）在 `BeanUtils.toBean` 后、insert 前追加：

```java
        // 主表产品编码=清单行物料编码去重拼接（勾选行决定）；名称/型号/原订单号退出写入
        entity.setProductCode(joinedProductCode(lines));
        entity.setProductName(null);
        entity.setProductModel(null);
        entity.setOriginalOrderNo(null);
```

`updateMaterialExchange`（`:143-170`）在 `BeanUtils.toBean(updateReqVO, MaterialExchangeDO.class)` 后、updateById 前追加同样四行（`update.` 前缀）。

新增私有方法（放在 `saveSerials` 前）：

```java
    /** 主表产品编码：清单行物料编码去重、半角逗号拼接；无清单行（仅旧行）返回 null。 */
    private String joinedProductCode(List<ExchangeLine> lines) {
        String joined = lines.stream().map(line -> line.scopeFact)
                .filter(Objects::nonNull)
                .map(DeliveryScopeLineFact::itemCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }
```

- [ ] **Step 4: 换货产品校验与快照服务端写入（saveSerials）**

新增私有方法（`resolveAndValidateLines` 后）：

```java
    /** 换货产品引用校验与快照解析：填写行按产品信息取 ACTIVE 快照，无效或停用拒绝；空集合返回空结果。 */
    private Map<Long, ProductOfficialSnapshot> resolveExchangeProductSnapshots(List<ExchangeLine> lines) {
        var productIds = lines.stream().map(line -> line.exchangeProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return assetProductOfficialApi.getActiveProductSnapshots(productIds).stream()
                .collect(Collectors.toMap(ProductOfficialSnapshot::id, Function.identity()));
    }
```

`saveSerials`（`:321-348`）签名与快照写入改为：

```java
    /** 持久化设备行服务器快照；显式行顺序与客户端一致，数量按行引用回填，换货产品快照由服务端按引用写入。 */
    private void saveSerials(Long exchangeId, List<ExchangeLine> lines, Map<String, BigDecimal> quantities,
                             Map<Long, ProductOfficialSnapshot> productSnapshots) {
        for (ExchangeLine line : lines) {
            MaterialExchangeSerialDO row = new MaterialExchangeSerialDO();
            row.setExchangeId(exchangeId);
            row.setQuantity(quantities.getOrDefault(line.refKey(), BigDecimal.ONE));
            if (line.scopeRef != null) {
                DeliveryScopeLineFact fact = line.scopeFact;
                row.setScopeDetailId(fact.scopeDetailId());
                row.setScopeId(fact.scopeId());
                row.setOrderNo(fact.orderNo());
                row.setLineNo(fact.lineNo());
                row.setItemCode(fact.itemCode());
                row.setProductName(fact.productName());
                row.setProductCode(fact.productCode());
                row.setDeviceTypeCode(fact.deviceTypeCode());
                row.setDeviceTypeName(fact.deviceTypeName());
            } else {
                SelectedProjectDevice device = line.device;
                row.setDeviceId(device.equipmentId());
                row.setSn(device.sn());
                row.setProductName(device.name());
                row.setProductCode(device.productCode());
                row.setProductModel(device.productModel());
                row.setContractNo(device.contractNo());
            }
            if (line.exchangeProductId != null) {
                ProductOfficialSnapshot snapshot = productSnapshots.get(line.exchangeProductId);
                if (snapshot == null) {
                    throw exception(MATERIAL_EXCH_PRODUCT_INVALID);
                }
                row.setProductId(snapshot.id());
                row.setProductName(snapshot.productName());
                row.setProductCode(snapshot.productCode());
                row.setProductModel(snapshot.productModel());
            } else {
                row.setProductId(null);
            }
            serialMapper.insert(row);
        }
    }
```

`createMaterialExchange`/`updateMaterialExchange` 的 saveSerials 调用改为：

```java
        saveSerials(entity.getId(), lines, serialQuantities(createReqVO),
                resolveExchangeProductSnapshots(lines));
```

（update 路径同形：`saveSerials(existing.getId(), lines, serialQuantities(updateReqVO), resolveExchangeProductSnapshots(lines))`。）

- [ ] **Step 5: 失败测试（先写新增用例）**

`MaterialExchangeServiceTest` 追加用例（复用该测试既有 mock 基建；字段引用同步更名 materialCode→productCode/name→productName/equipmentId→deviceId）：

```java
    @Test
    void createJoinsDistinctItemCodesAndExitsLegacyFacts() {
        // 两个清单行同 itemCode（ITEM-SEC-DEPLOY）+ 一个不同（ITEM-SEC-DEPLOY-2）→ 拼接去重两值
        MaterialExchangeSaveReqVO reqVO = validCreateReqVO(); // 既有基建
        MaterialExchangeSerialVO first = serialOf(993109130001L, "ITEM-SEC-DEPLOY", null);
        MaterialExchangeSerialVO second = serialOf(993109130002L, "ITEM-SEC-DEPLOY", null);
        MaterialExchangeSerialVO third = serialOf(993109130003L, "ITEM-SEC-DEPLOY-2", null);
        reqVO.setSerials(new ArrayList<>(List.of(first, second, third)));
        reqVO.setQuantity(new BigDecimal("4"));
        mockScopeFacts(first, second, third); // 既有 fact mock 基建按 itemCode 返回

        service.createMaterialExchange(reqVO);

        ArgumentCaptor<MaterialExchangeDO> captor = ArgumentCaptor.forClass(MaterialExchangeDO.class);
        verify(materialExchangeMapper).insert(captor.capture());
        assertEquals("ITEM-SEC-DEPLOY,ITEM-SEC-DEPLOY-2", captor.getValue().getProductCode());
        assertNull(captor.getValue().getProductName());
        assertNull(captor.getValue().getProductModel());
        assertNull(captor.getValue().getOriginalOrderNo());
        assertNull(captor.getValue().getDeviceId());
    }

    @Test
    void createRejectsInactiveProduct() {
        MaterialExchangeSaveReqVO reqVO = validCreateReqVO();
        MaterialExchangeSerialVO row = serialOf(993109130001L, "ITEM-SEC-DEPLOY", 404L);
        reqVO.setSerials(new ArrayList<>(List.of(row)));
        mockScopeFacts(row);
        when(assetProductOfficialApi.getActiveProductSnapshots(List.of(404L))).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> service.createMaterialExchange(reqVO));
    }

    @Test
    void createWritesServerSideSnapshot() {
        MaterialExchangeSaveReqVO reqVO = validCreateReqVO();
        MaterialExchangeSerialVO row = serialOf(993109130001L, "ITEM-SEC-DEPLOY", 11L);
        reqVO.setSerials(new ArrayList<>(List.of(row)));
        mockScopeFacts(row);
        when(assetProductOfficialApi.getActiveProductSnapshots(List.of(11L))).thenReturn(List.of(
                new ProductOfficialSnapshot(11L, "01100003", "DPtech IPS2000-MA-N", "IPS2000-MA-N+1Y")));

        service.createMaterialExchange(reqVO);

        ArgumentCaptor<MaterialExchangeSerialDO> rowCaptor = ArgumentCaptor.forClass(MaterialExchangeSerialDO.class);
        verify(serialMapper, atLeastOnce()).insert(rowCaptor.capture());
        assertEquals(11L, rowCaptor.getValue().getProductId());
        assertEquals("01100003", rowCaptor.getValue().getProductCode());
        assertEquals("DPtech IPS2000-MA-N", rowCaptor.getValue().getProductName());
        assertEquals("IPS2000-MA-N+1Y", rowCaptor.getValue().getProductModel());
    }
```

（`serialOf`/`mockScopeFacts` 按测试类既有工具组合实现；若无则新增私有工具， fact mock 返回 `DeliveryScopeLineFact` 带 itemName 事实。）

- [ ] **Step 6: 跑测试**

Run: `mvn -q test -pl pms-module-engineering -o -Dtest=MaterialExchangeServiceTest,MaterialExchangeSerialServiceTest`
Expected: 全绿（既有用例更名后 + 新增 3 用例）

- [ ] **Step 7: 提交**

```bash
git add pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/materialexchange/ pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/materialexchange/
git commit -m "feat(pms): 换货保存联动多值编码拼接与换货产品快照服务端写入"
```

---

### Task 6: 前端 API 类型与换货产品下拉

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/material-exch/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/product-official.ts`

- [ ] **Step 1: 换货 API 类型更名**

`material-exch/index.ts`：`MaterialExchangeVO`/`MaterialExchangeSaveReqVO` 中 `materialCode`→`productCode`、`materialName`→`productName`、`specification`→`productModel`、`equipmentId`→`deviceId`、`newEquipmentId`→`newDeviceId`；`MaterialExchangeSerialVO`（子表）中 `name`→`productName`、`equipmentId`→`deviceId`、新增 `productId?: number`。

- [ ] **Step 2: 产品信息下拉 API**

```typescript
import request from '@/config/axios'

export interface ProductOfficialVO {
  id?: number
  productCode?: string
  productName?: string
  productModel?: string
  status?: string
}

export const AssetProductOfficialApi = {
  page: (params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    request.get({ url: '/api/v1/pms/asset-product-officials/page', params }) as Promise<{
      total: number
      list: ProductOfficialVO[]
    }>
}
```

- [ ] **Step 3: 提交**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/material-exch/index.ts yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/product-official.ts
git commit -m "feat(pms-ui): 产品信息下拉 API 与换货类型更名对齐"
```

---

### Task 7: 前端视图（picker 行内下拉/明细/列表/表单/详情/DeviceTag）

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/material-exch/MaterialDevicePicker.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/material-exch/index.vue`
- Rename: `yudao-ui/yudao-ui-admin-vue3/src/components/EquipmentTag/` → `DeviceTag/`（含 index.vue 更名 DeviceTag.vue，全部引用页面同步 import：工程配置/安装/联调 workspace、设备台账 config-log 及 runtime spec）

- [ ] **Step 1: picker 已选行表增加换货产品列（行内下拉）**

`MaterialDevicePicker.vue` 的已选行表（`:27-42`）在 换货数量 列前插入：

```vue
      <el-table-column label="换货产品" min-width="180">
        <template #default="{ row }">
          <ProductOfficialSelect :model-value="row.productId" size="small"
            @update:model-value="setExchangeProduct(keyOf(row), $event)" />
        </template>
      </el-table-column>
```

组件新增 `ProductOfficialSelect.vue`（同目录，可留空+关键字+停用置灰）：

```vue
<template>
  <el-select :model-value="modelValue" filterable remote clearable placeholder="可留空" size="small"
    class="!w-170px" aria-label="换货产品" :remote-method="loadOptions" :loading="loading"
    @focus="loadOptions('')" @clear="handleClear" @update:model-value="handleUpdate">
    <el-option v-for="item in options" :key="item.id" :value="item.id"
      :label="`${item.productCode} ${item.productName}`" :disabled="item.status !== 'ACTIVE'" />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AssetProductOfficialApi, ProductOfficialVO } from '@/api/pms/asset/product-official'

defineProps<{ modelValue?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', value?: number): void }>()

const options = ref<ProductOfficialVO[]>([])
const loading = ref(false)
const loadOptions = async (keyword: string) => {
  loading.value = true
  try {
    options.value = (await AssetProductOfficialApi.page({ pageNo: 1, pageSize: 50, keyword })).list
  } finally {
    loading.value = false
  }
}
const handleUpdate = (value?: number) => emit('update:modelValue', value)
const handleClear = () => emit('update:modelValue', undefined)
onMounted(() => loadOptions(''))
</script>
```

`MaterialDevicePicker.vue` script 增加（`setQuantity`/`remove` 旁）：

```typescript
const setExchangeProduct = (key: string, productId?: number) => {
  const row = modelValue.value.find((item) => keyOf(item) === key)
  if (row) row.productId = productId
}
```

（`modelValue`/`keyOf` 复用该组件既有 reactive 模式；行对象为本地 VO，`productId` 字段经 Task 6 类型可用。）

- [ ] **Step 2: 明细行表换货产品列（展示行：服务端快照名）**

`index.vue` 明细行表（`:306-317`）在 换货数量 列前插入展示列：

```vue
      <el-table-column label="换货产品" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.productName || '-' }}
        </template>
      </el-table-column>
```

（明细行表为只读展示；行内下拉编辑入口在 picker 已选行表。）

- [ ] **Step 3: 列表页与表单（Q1）**

`index.vue`：

- 列表 列调整：`物料名称` 列（`:74` prop="materialName"）替换为物料编码多值标签列：

```vue
      <el-table-column label="物料编码" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="code in (row.productCode || '').split(',').filter(Boolean)" :key="code"
            size="small" class="mr-4px">{{ code }}</el-tag>
          <span v-if="!row.productCode">-</span>
        </template>
      </el-table-column>
```

- 删除 `名称` 列（`:68` prop="name"）与 `原订单号` 列（`:80` prop="originalOrderNo"）
- 表单：`名称` 表单项（`:18-20`）加 `v-if="!props.projectId"`（项目内隐藏，Q1）
- 编辑器：主表物料编码多值标签只读派生展示（设备清单表单区 `:164` 后追加）：

```vue
      <el-form-item label="物料编码">
        <el-tag v-for="code in derivedItemCodes" :key="code" size="small" class="mr-4px">{{ code }}</el-tag>
        <span v-if="!derivedItemCodes.length">-</span>
      </el-form-item>
```

```typescript
const derivedItemCodes = computed(() =>
  [...new Set((form.serials || []).map((row) => row.itemCode).filter(Boolean))])
```

- 详情（descriptions）：删除 `关联设备` 项（`:279-282`）与 `物料名称` 项；字段引用更名（`current.materialName`→`current.productCode` 等）

- [ ] **Step 4: 全组件更名 EquipmentTag→DeviceTag**

```bash
git mv yudao-ui/yudao-ui-admin-vue3/src/components/EquipmentTag yudao-ui/yudao-ui-admin-vue3/src/components/DeviceTag
```

组件内文件与组件 name 更名；全局 import 同步（`grep -rln "EquipmentTag" yudao-ui/yudao-ui-admin-vue3/src` 列出全部引用页，逐一改 `import ... from '@/components/DeviceTag/index.vue'`；换货页本次移除其用法，不出现在引用清单属正常）。

- [ ] **Step 5: 受影响 runtime spec 与单测**

Run: `pnpm exec vitest run src/views/pms/engineering/material-exch src/components/DeviceTag src/views/pms/engineering/configuration src/views/pms/engineering/joint-test`
Expected: 新增改动相关用例绿；既有环境性失败（CSS runtime）按甄别方法记录不扩大

- [ ] **Step 6: 提交**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/material-exch/ yudao-ui/yudao-ui-admin-vue3/src/components/
git commit -m "feat(pms-ui): 换货行内换货产品下拉与界面 Q1 对齐（DeviceTag 更名）"
```

---

### Task 8: V357 种子迁移（组合场景覆盖）

**Files:**
- Create: `sql/migrations/V357__material_exchange_product_naming_seed.sql`

- [ ] **Step 1: 写种子（幂等、creator 标识、高段 ID、真实产品引用）**

```sql
-- 换货申请种子：覆盖换货产品已选/未选、多值编码、快照组合；引用 ast_product_official_info 真实行，不臆造取值
-- 场景A：两清单行不同物料编码 → 主表 product_code 两值拼接；首行换货产品已选（真实 ACTIVE 引用）
INSERT INTO imp_eng_material_exchange (id, project_id, code, name, exchange_type, product_code, quantity, unit, reason, status, crm_push_status, version, applicant_user_id, apply_time, creator)
SELECT 9000000001, 1010, 'ME-SEED-2026-9001', '种子：多值编码与换货产品已选', 'INCOMPATIBLE',
       'ITEM-SEC-DEPLOY,ITEM-SEC-DEPLOY-2', 3, '台', '种子数据：覆盖多值拼接与已选快照组合', 0, 'PENDING', 0, 1, NOW(), 'seed'
WHERE NOT EXISTS (SELECT 1 FROM imp_eng_material_exchange WHERE code = 'ME-SEED-2026-9001' AND deleted = b'0');

INSERT INTO imp_eng_material_exchange_serial (id, exchange_id, device_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code, device_type_code, device_type_name, product_id, product_code, product_name, product_model, creator)
SELECT 9000000011, 9000000001, NULL, 1, 993109130001, 993109120001, 'SO-EQUIP-DEMO', '1', 'ITEM-SEC-DEPLOY', 'AR', '华为AR6280路由器',
       p.id, p.product_code, p.product_name, p.product_model, 'seed'
FROM ast_product_official_info p
WHERE p.product_code = '01100003' AND p.status = 'ACTIVE' AND p.deleted = b'0'
  AND EXISTS (SELECT 1 FROM imp_eng_material_exchange WHERE id = 9000000001)
  AND NOT EXISTS (SELECT 1 FROM imp_eng_material_exchange_serial WHERE id = 9000000011 AND deleted = b'0');

INSERT INTO imp_eng_material_exchange_serial (id, exchange_id, device_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code, device_type_code, device_type_name, product_id, creator)
SELECT 9000000012, 9000000001, NULL, 2, 993109130002, 993109120001, 'SO-EQUIP-DEMO', '2', 'ITEM-SEC-DEPLOY-2', 'S5735', '48口千兆接入交换机',
       NULL, 'seed'
WHERE EXISTS (SELECT 1 FROM imp_eng_material_exchange WHERE id = 9000000001)
  AND NOT EXISTS (SELECT 1 FROM imp_eng_material_exchange_serial WHERE id = 9000000012 AND deleted = b'0');

-- 场景B：单清单行未选换货产品（草稿后补），主表单值编码
INSERT INTO imp_eng_material_exchange (id, project_id, code, name, exchange_type, product_code, quantity, unit, reason, status, crm_push_status, version, applicant_user_id, apply_time, creator)
SELECT 9000000002, 1010, 'ME-SEED-2026-9002', '种子：未选换货产品草稿', 'DAMAGE',
       'ITEM-SEC-DEPLOY', 1, '台', '种子数据：覆盖未选后补组合', 0, 'PENDING', 0, 1, NOW(), 'seed'
WHERE NOT EXISTS (SELECT 1 FROM imp_eng_material_exchange WHERE code = 'ME-SEED-2026-9002' AND deleted = b'0');

INSERT INTO imp_eng_material_exchange_serial (id, exchange_id, device_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code, device_type_code, device_type_name, creator)
SELECT 9000000013, 9000000002, NULL, 1, 993109130003, 993109120001, 'SO-EQUIP-DEMO', '3', 'ITEM-SEC-DEPLOY', 'WLAN-AP', '无线接入点', 'seed'
WHERE EXISTS (SELECT 1 FROM imp_eng_material_exchange WHERE id = 9000000002)
  AND NOT EXISTS (SELECT 1 FROM imp_eng_material_exchange_serial WHERE id = 9000000013 AND deleted = b'0');
```

（场景A 行2 的 product_id=NULL 覆盖"同一申报内已选/未选混合"；`status=0` 草稿可编辑用于浏览器闭环。种子列名使用 V356 更名后列名。`project_id=1010` 若本地库无该项目则按本地实际项目 id 调整，种子其余不变。）

- [ ] **Step 2: 应用与落库校验**

Run: `docker exec npdms-domain-test-mysql-1 mysql -uroot -p<密码> npdms_domain_test -e "SELECT code, product_code FROM imp_eng_material_exchange WHERE creator='seed'; SELECT id, item_code, product_id, product_code, product_name, product_model FROM imp_eng_material_exchange_serial WHERE creator='seed'"`
Expected: 2 申报 + 3 子行；场景A 行1 快照组=真实产品行值；重跑幂等（行数不变）

- [ ] **Step 3: 提交**

```bash
git add sql/migrations/V357__material_exchange_product_naming_seed.sql
git commit -m "feat(pms): 换货申请种子迁移（多值/已选/未选组合）"
```

---

### Task 9: 全量验证

- [ ] **Step 1: 后端重启应用 V356/V357 并 Flyway 校验**

Run: `.run/start-domain-test.ps1`（或既有启动方式）后 `curl -s http://127.0.0.1:59191/actuator/health`
Expected: UP；`flyway_schema_history` 出现 V356/V357 成功行；`SHOW CREATE TABLE imp_eng_material_exchange_serial` 确认 `active_ref` 生成列引用已随 RENAME 自动更新为 `device_id`

- [ ] **Step 2: 后端受影响测试重跑**

Run: `mvn -q test -pl pms-module-asset,pms-module-engineering -o`
Expected: 本改动相关用例全绿；既有无关失败如实记录（并行迁移契约类 ArrivalAcceptance/RequirementAnalysis 等不属本改动）

- [ ] **Step 3: 真实浏览器业务闭环（10.210.0.11:19191）**

1. 换货列表页：物料编码标签列、名称/原订单号列已移除
2. 新建申报：勾选清单行 → 已选行表行内下拉选换货产品（关键字搜索生效、停用行置灰）→ 另一行留空 → 保存
3. 落库核验：主表 product_code=去重拼接值；已选行快照组=产品信息真实行值；未选行 product_id/快照为 NULL
4. 编辑再保存：退出字段仍 NULL、拼接值不变
5. 换货产品留空草稿后补：编辑选产品保存生效
6. 明细/详情：换货产品列展示快照名、关联设备/物料名称已移除；HTTP 200 ≠ 业务成功一律查落库

- [ ] **Step 4: 汇报**

- 结论先行汇报：迁移应用、测试结果（含无关失败甄别）、浏览器闭环证据；不自动推送
