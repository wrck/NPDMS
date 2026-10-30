# TF-011 现有夹具只读盘点

2026-10-29；只读数据库事实时点：14:28:37.389（数据库时间）。无数据写入、迁移、DDL、授权修改或服务操作。

## 已锁定的独立基准

- 本人账号 `l04533`，用户ID `2112914651708764190`，租户 `1`，当前启用、未删除；当前有效系统角色 `PROJECT_MANAGER`。`h06523` 同为项目经理候选；`z01010` 同时持有 PROJECT_MANAGER 和 SERVICE_MANAGER，不能称为纯服务经理身份。
- 本人真实普通项目列表去除筛选后遍历100+56行，共156唯一项目编码。全部编码均唯一映射到租户1现存项目；全部156项目具有本人当前ACTIVE、已确认、区间有效的独立成员事实，且全部具有最新ACTIVE树版本的root→node路径。因此不把SUMMARY或祖先占位当作FULL来源。
- 此处FULL基准来自独立当前成员事实、当前树路径及运行授权XML的契约。未捕获实际Owner prepare响应；未手工制造Owner输入，未将候选API返回反推为期望集合。不能据此声称实际Owner prepare链路耗时已通过。
- 运行后端：PID `41264`（观测时），59191，`local,dac`；`.run/dac-route-server.jar` SHA-256 `27673b89bbe9573a07b09beef571ac1b1cf12471b18765a83ef6c23f4a4f1cf8`。运行包已包含member-filter-options路由、selectMemberFilterUserIds和已确认授权条件；这些标记只证明对应实现存在，不证明浏览器全部验收通过。

## 独立候选期望

| field | total | 当前账号（按SYSTEM用户ID升序） |
|---|---:|---|
| MANAGER | 8 | c03724、f06009、f06054、l04533、l05561、w02127、x05721、z05987 |
| SERVICE_MANAGER | 3 | c03724、w02127、x05721 |
| SALES | 0 | 空 |

MANAGER按正式角色别名包含PROJECT_MANAGER和服务经理；PROJECT_MANAGER事实独有人群6人，SERVICE_MANAGER事实3人，x05721重合，去重后8人。全部8人均有当前账号/名称与成员快照不一致的现成事实，可验证不能用快照搜索。机器可读文件保留真实字符串ID和事实来源，未记录人名、客户/项目名、电话、邮件或Secret。

`f06009`当前有效管理成员事实覆盖130项目，其中仅两项目处于本人156FULL基准：`61388 / 16500024030402N-0`、`66998 / 16500024102801N-0`。本人浏览器选中该账号后返回的两编码与独立交集完全一致，另外128项目未因共享候选身份扩大返回。此证据覆盖F05的U_SHARED分支，不替代U_OUT-only分支。

## 现成样本与未执行边界

F01当前账号与快照不一致、F04跨项目重复、F05共享成员样本均存在。F02仅有generic SERVICE_MANAGER，当前基准没有L1/L2。F03缺启用SYSTEM销售唯一正例；F08缺当前未确认候选；F10全部8候选均有匹配系统角色，不能人为撤角色来造样本。

F06精确时间/删除边界、F07无任何独立FULL来源、F09唯一停用/删除/跨租户来源、F11部门可见/不可见、F12无FULL/SUMMARY/Office、F14恶意参数尚未完成真实运行验收。部门ID不代表Dept Owner可见性；物理确认位0不代表没有其他授权来源。既有隔离失败测试继续作为离线证据，缺样本记未执行。

F13本人浏览器已观察账号命中和不存在关键词空结果；pageSize=1全分页、冷次及后两次完整计时、快速切词旧响应行为仍须分别记录，不能用短等待或平均值宣称1秒达标。

## 取证文件

- `tf011-fixture-manifest-readonly-v2.json`：脱敏当前事实、SQL与参数、SYSTEM/角色状态、成员区间与确认位、项目版本、树版本及运行包条目哈希。连接只执行SELECT和READ ONLY事务设置，结束ROLLBACK；会话默认READ ONLY=1。
- `tf011-expected-candidates-readonly.json`：精确候选ID集合、F01~F14逐项可用性、f06009独立交集核对。
- `tf011-f06009-shared-member-readonly.json`：130个共享成员项目及成员ID、区间、版本。
- `tf011-l04533-project-list-browser.json`、`tf011-l04533-filtered-browser.json`：父任务采集的本人真实UI基准及选人后结果。
- 生成脚本：`.run/feedback-20261028/acceptance/tf011-fixture-inventory-readonly.py`；首次manifest保留原件，v2补会话READ ONLY标记及当前树路径，未覆盖旧证据。
