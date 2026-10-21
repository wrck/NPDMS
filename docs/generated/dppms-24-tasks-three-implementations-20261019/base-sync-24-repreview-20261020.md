# 新虚基类24项独立任务：全量真实预览复验

核验时间：2026-10-20T14:20:08.526239+08:00（Asia/Shanghai）。分支`fix/dppms-migration-consolidation-20261012`；服务59284；目标仅`127.0.0.1:24306/npdms_reverify_20261014`。

本轮重新调用全部24项新BASE任务的preview入口，新增24条preview=true回执。状态统计：{"PREVIEW_READY": 24}；API或任务失败：0。本轮空预览：P30。预览终态不表示正式数据输入已齐备、已完成同步或已获准真实写入。

## 本轮逐项回执

| 任务 | 名称 | 新Run | 终态 | read | 较此前最新read变化 | 规划insert / update / unchanged / delete | 秒 |
|---|---|---|---|---:|---:|---|---:|
| P01 | 公司与部门独立目录 | 2112425268933046274 | PREVIEW_READY | 208 | +0 | 0 / 0 / 208 / 0 | 0.472 |
| P04 | 账号基础（任职关系规格待闭合） | 2112425270786928641 | PREVIEW_READY | 12,632 | +0 | 12,605 / 0 / 27 / 0 | 0.861 |
| P08 | 系统字典类型及字典项 | 2112425274729574401 | PREVIEW_READY | 612 | +0 | 611 / 0 / 1 / 0 | 0.203 |
| P10 | CRM客户主档 | 2112425275853647873 | PREVIEW_READY | 59,544 | +0 | 59,544 / 0 / 0 / 0 | 20.213 |
| P11 | CRM产品主档 | 2112425360834441218 | PREVIEW_READY | 8,443 | +0 | 0 / 0 / 0 / 0 | 0.354 |
| P12 | DPPMS缺失产品补齐 | 2112425362642186241 | PREVIEW_READY | 34,976 | +0 | 34,976 / 0 / 0 / 0 | 0.753 |
| P13 | 项目执行单头 | 2112425366081515522 | PREVIEW_READY | 99,895 | +0 | 0 / 0 / 0 / 0 | 10.918 |
| P14 | ERP销售订单与订单行 | 2112425412210470914 | PREVIEW_READY | 480,044 | +0 | 478,464 / 0 / 0 / 0 | 70.386 |
| P15 | 合同及全量发货历史 | 2112425707665633281 | PREVIEW_READY | 4,708,483 | +1,583 | 4,708,483 / 0 / 0 / 0 | 245.781 |
| P16 | 项目主档及独立来源身份 | 2112426738885603329 | PREVIEW_READY | 158,871 | +0 | 79,429 / 0 / 79,442 / 0 | 131.187 |
| P18 | 项目合同关系历史 | 2112427289358643202 | PREVIEW_READY | 82,676 | +0 | 0 / 0 / 0 / 0 | 10.775 |
| P19 | 项目相关方及历史状态属性 | 2112427334816509953 | PREVIEW_READY | 186,118 | +0 | 186,118 / 0 / 0 / 0 | 7.679 |
| P20 | 项目真实产品历史清单 | 2112427367251062786 | PREVIEW_READY | 7,818 | +0 | 7,818 / 0 / 0 / 0 | 8.801 |
| P21 | 租赁与配置展示缓存 | 2112427404450344962 | PREVIEW_READY | 1,190 | +0 | 1,190 / 0 / 0 / 0 | 0.150 |
| P22 | 项目产品实施范围历史 | 2112427405406646273 | PREVIEW_READY | 343,858 | +0 | 340,882 / 0 / 0 / 0 | 15.309 |
| P23 | 合同收款计划展示缓存 | 2112427469931819010 | PREVIEW_READY | 35,501 | +0 | 35,501 / 0 / 0 / 0 | 0.894 |
| P24 | 合同回款信息 | 2112427474067402754 | PREVIEW_READY | 81,547 | +0 | 81,540 / 0 / 7 / 0 | 5.662 |
| P25 | 设备续保历史 | 2112427498130124802 | PREVIEW_READY | 462,213 | +0 | 462,213 / 0 / 0 / 0 | 12.872 |
| P30 | 正式发货设备基础承接 | 2112427552333115394 | PREVIEW_READY | 0 | +0 | 0 / 0 / 0 / 0 | 0.121 |
| P31 | 项目成员及最终客户联系人历史 | 2112427553113255938 | PREVIEW_READY | 381,195 | +0 | 381,195 / 0 / 0 / 0 | 20.654 |
| P33 | 设备安装历史 | 2112427640023429121 | PREVIEW_READY | 1,035,329 | +0 | 0 / 0 / 0 / 0 | 143.172 |
| P34 | 出厂版本历史 | 2112428241000083457 | PREVIEW_READY | 165,801 | +0 | 165,801 / 0 / 0 / 0 | 4.080 |
| P35 | 软件批次及版本历史 | 2112428258494525442 | PREVIEW_READY | 1,586,578 | +0 | 1,586,578 / 0 / 0 / 0 | 62.450 |
| P37 | 整表许可历史 | 2112428520672079874 | PREVIEW_READY | 475,468 | +0 | 475,468 / 0 / 0 / 0 | 17.853 |

上表insert/update等为预览规划数量。各任务读取可能覆盖多个实体、同一业务来源或历史记录，read之和不代表去重业务总量；本轮正式业务写入为0，由前后全部字段摘要验证。逐任务起止时间、API结果及旧Run对照见同名JSON。

24项告警原文均与复验前各项最新回执一致，无新增告警。

## 读取量变化

- P15：此前4,706,900 → 本轮4,708,483（+1,583）。

P15本轮五阶段运行日志核对：ShipmentContract=120,638、Package=156,368、ShipmentEvent=4,283,326、DeviceRelationship=65,046、Contract=83,105，总和与Run一致。前四阶段数量均未变；差异全部来自归并合同组。

合同阶段有效订单输入为93,216（较旧回执+1,651），合同组为83,105（+1,583），sourceIssues=0。当前冻结包与上次P15成功包的17个P15类族/来源XML逐项相同，差异项为0，支持这是来源结果变化，不是本轮修改选客或过滤规则。未推定哪些来源记录被外部新增/修正，未再次全量读源。

## 本轮回执仍登记的缺项与已决规则

以下保留真实回执原文。预览允许登记的正式输入、正式执行结构前置及已决暂缓，不改写成任务失败，也不隐去为“全部可正式执行”。

### P01 公司与部门独立目录

```text
P01_DIRECTOR_ASSOCIATION_PENDING:162007003;P01_DIRECTOR_ASSOCIATION_PENDING:162012003;P01_DIRECTOR_ASSOCIATION_PENDING:162022003;P01_DIRECTOR_ASSOCIATION_PENDING:310409001;P01_DIRECTOR_ASSOCIATION_PENDING:310422001;P01_DIRECTOR_ASSOCIATION_PENDING:167010;P01_DIRECTOR_ASSOCIATION_PENDING:167020;P01_DIRECTOR_ASSOCIATION_PENDING:167050
```

### P04 账号基础（任职关系规格待闭合）

```text
P04_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P04_ORIGINAL_READ:3307;EXCLUDED_ACCOUNTS:26;ORPHAN_PROFILES:3
```

### P13 项目执行单头

```text
P13_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P13_OUTSIDE_REVIEWED_PROJECT_SCOPE:2625;P13_PREVIEW_SOURCE_VALIDATED;WRITE_COUNTS_DEFERRED_UNTIL_EXECUTION
```

### P14 ERP销售订单与订单行

```text
P14_ORDER_REFERENCE_PENDING:1580
```

### P15 合同及全量发货历史

```text
P15_SOURCE_PREVIEW:FORMAL_BASE_P14_P24_INPUTS_NOT_VERIFIED
```

### P16 项目主档及独立来源身份

```text
P16_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P16_ALIASES_REQUIRE_NEW_MASTERS:79442
```

### P18 项目合同关系历史

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P18_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P18_COMPANY_AUTHORIZATION_PENDING:001=80374,002=1804,003=195;P18_COMPANY_SOURCE_PENDING:8;P18_CONTRACT_REFERENCE_PENDING:82381
```

### P19 项目相关方及历史状态属性

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P19_EXTENSION_ATTRIBUTES_DEFERRED:shipment_status,shipment_status_updated_at,execution_status,execution_status_updated_at
```

### P20 项目真实产品历史清单

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED
```

### P21 租赁与配置展示缓存

```text
P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable
```

### P22 项目产品实施范围历史

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P22_DEPARTMENT_ID_POSTFILL_PENDING:-1;P22_ORDER_LINE_ID_POSTFILL_PENDING:-1;P22_EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference;P22_ZERO_QUANTITY_PENDING:2976;P22_DEPARTMENT_SOURCE_MISSING_PENDING:14293;P22_EXECUTION_SCHEMA_REQUIRED:department_pending_reference
```

### P30 正式发货设备基础承接

```text
P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0
```

### P31 项目成员及最终客户联系人历史

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P31_SOURCE_INTERVAL_PENDING:152
```

### P33 设备安装历史

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P33_TRANSFER_DEFERRED:2;P33_FORMAL_DEVICE_PENDING:1035329;P33_BLANK_ADDRESS_FILTERED_AFTER_READ:2386
```

### P35 软件批次及版本历史

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P35_EXTENSION_ATTRIBUTES_DEFERRED:conp_bak,conp_change,boot_bak,boot_change,cpld_bak,cpld_change,pcb_bak,pcb_change
```

### P37 整表许可历史

```text
P37_LICENSE_CODE_EMPTY:sourceId=472122;P37_DEVICE_ASSOCIATION_PENDING:475468;P37_CONTRACT_ASSOCIATION_PENDING:475468
```

- P30若read=0且正式DPPMS发货缺失，仍为空预览，不代表已建立正式设备。
- P33设备缺项按真实回执计数，未造设备ID或可写数量；来源SN、匹配SQL及32个特殊SN详见[只读设备引用追溯](P33-formal-device-reference-audit-20261020.md)。两个已决串货sourceId1161000、1161002继续暂缓，不重新裁决。
- P10名称255、P33安装位置1024沿用已授权补丁；本轮不执行任何DDL。原文、空地址过滤和快照承接规则保持。
- P22的-1后置回填、P31原日期登记、P37原始空许可码加密保留及拓展属性暂缓，均沿已有结论，不自动扩写规则。

## 运行恢复与保护验证

本轮开始59284未监听，MySQL/Redis已运行，迁移容器exited。仅恢复原冻结候选包，当前PID10736；未停止任何服务或操作59191。首次启动因当前会话未继承DAC HTTPS环境而失败；保留失败日志后，专用启动器恢复现有加密凭据和既有HTTPS端点，未修改产品代码、冻结包或启动DAC。

- 169张原任务/业务/独立绑定表的全部字段摘要无差异；473张表结构无差异。
- 180项保护快照无差异；旧BASE回执全部字段未变，新任务配置保持，运行占用全部释放。
- 本轮恰好新增24条独立preview=true回执；新增非预览运行0，正式业务数据与绑定未写入。
- 当前进程Flyway、Flowable自动结构更新、Quartz、Batch和事件驱动关闭；迁移容器exited。
- 未调用原任务、execute、迁移、来源修正或业务后处理；未写测试库npdms_domain_test；59191未操作。

本地证据：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-24-repreview-20261020/`，包括baseline.json、protected-before.json、各Pxx-preview.json、verification-proof.json和after-state.json。执行入口reverify.py；报告入口build-report.py。

[全部新回执、旧读取量和保护结果](base-sync-24-repreview-20261020.json)。

正常认证/审计写入引起的AUTO_INCREMENT计数变化单列如下；它们不改变表结构，业务表自增计数无变化，未执行DDL：

```json
{
  "system_oauth2_refresh_token": {
    "before": [
      "3529"
    ],
    "after": [
      "3530"
    ]
  },
  "system_login_log": {
    "before": [
      "5514"
    ],
    "after": [
      "5515"
    ]
  },
  "system_oauth2_access_token": {
    "before": [
      "108760"
    ],
    "after": [
      "108761"
    ]
  }
}
```
