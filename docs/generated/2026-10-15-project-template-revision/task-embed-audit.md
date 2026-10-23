# 五类模板任务内嵌页面核查（2026-10-16）

来源：正式模板只读 API 回读及 19191 真实浏览器，未发布、未写入业务数据。

## 草稿状态

| 模板 | 状态 | 版本 | 任务数 | 内容保持原样 |

| --- | --- | --- | --- | --- |

| 普通类－直签（993009900032） | DRAFT | 9 | 16 | True |

| 工程类－直签（993009900033） | DRAFT | 7 | 16 | True |

| 普通类－非直签（993009900034） | DRAFT | 7 | 13 | True |

| 工程类－非直签（993009900035） | DRAFT | 7 | 13 | True |

| 非直签－原厂督导（993009900036） | DRAFT | 7 | 12 | True |



## 绑定核查

70 个任务归并为 16 种任务代码。下表浏览器结果为现有隔离项目中相同绑定的代表路径，不表示逐份草稿实例化了 70 次任务。

| 任务 | 五份草稿绑定 | 浏览器证据 |

| --- | --- | --- |

| 人员指派 / PROJECT_ASSIGNMENT | BUSINESS_OBJECT / PROJ_PROJECT_ASSIGNMENT | 项目64任务内嵌内容可查看 |

| 用户信息录入 / PRE_CONTACT | PAGE / /pms/project-management/project-master-detail | 项目64任务内嵌内容可查看 |

| 现场工勘 / PRE_SURVEY | BUSINESS_COMPONENT / SOL_SITE_SURVEY | 项目64任务内嵌内容可查看 |

| 需求分析 / PRE_REQUIREMENT | BUSINESS_OBJECT / PROJ_REQUIREMENT_ANALYSIS | 项目64任务内嵌内容可查看 |

| 施工计划 / PLAN_CONSTRUCTION | BUSINESS_OBJECT / PLN_STAGE_PLAN_APPROVAL | 项目64任务内嵌内容可查看 |

| 实施方案 / PLAN_SOLUTION | BUSINESS_OBJECT / SOL_IMPLEMENTATION_SOLUTION | 项目64任务内嵌内容可查看 |

| 到货签收 / DEPLOY_ARRIVAL | TASK_NATIVE / TASK_NATIVE | 按已批准变更保持 TASK_NATIVE 文件模式；核对交付件配置，共用弹窗已实测，未创建这三项的新草稿实例 |

| 硬件实施 / DEPLOY_HARDWARE | BUSINESS_COMPONENT / IMP_PROJECT_INSTALLATION | 项目64任务内嵌内容可查看 |

| 配置调试 / DEPLOY_CONFIG | PAGE / /pms/engineering/execution/imp-configuration | 项目64任务内嵌内容可查看 |

| 业务联调 / DEPLOY_JOINT | PAGE / /pms/engineering/execution/imp-joint-test | 项目64任务内嵌内容可查看 |

| 割接上线 / DEPLOY_CUTOVER | BUSINESS_COMPONENT / CUT_CUTOVER_APPROVAL | 项目63旧绑定内嵌列表；项目64同组件已归档详情及两节点审批历史可查看 |

| 现场培训 / ACCEPT_TRAINING | BUSINESS_COMPONENT / IMP_TRAINING_CONFIRMATION | 项目64任务内嵌内容可查看 |

| 满意度调查 / ACCEPT_SATISFACTION | TASK_NATIVE / TASK_NATIVE | 项目64原生交付件清单及只读提交历史弹窗 |

| 初验 / ACCEPT_INITIAL | TASK_NATIVE / TASK_NATIVE | 按已批准变更保持 TASK_NATIVE 文件模式；核对交付件配置，共用弹窗已实测，未创建这三项的新草稿实例 |

| 终验 / ACCEPT_FINAL | TASK_NATIVE / TASK_NATIVE | 按已批准变更保持 TASK_NATIVE 文件模式；核对交付件配置，共用弹窗已实测，未创建这三项的新草稿实例 |

| 项目闭环 / PROJECT_CLOSURE | PAGE / /pms/project/normal-closure | 项目61任务内正常闭环检查页 |



## 修改及边界

硬件实施 IMP_PROJECT_INSTALLATION 改为复用 ProjectInstallationWorkbench；旧割接审批 CUT_CUTOVER_APPROVAL 改为复用 ProjectCutoverTasks。保留 Owner 身份校验、项目标识、QUERY 能力及 readonly 传递，不增添 CREATE 权限。人员指派包装页补传 readonly，闭环项目成员编辑按钮实测禁用。培训沿用上次已修复的内嵌组件。

BusinessView.runtime.spec.ts 定向测试 47/47 通过，包含正确组件、精确项目身份、只读能力及错误 Owner 拒绝。

历史项目61的到货仍是旧 IMP_ARRIVAL_RECEIPT 快照，显示原跳转入口的权限提示；它不对应当前五份草稿中的 TASK_NATIVE 文件模式，本次未覆盖历史冻结绑定。旧初验仍显示验收报告活动列表，同样未将历史实例冒充新文件模式验收。

本次只核查查看与内嵌路径，没有重新执行审批、上传、任务完成或项目闭环。浏览器还显示部分既有配置／联调／施工计划／方案页面的新增入口；未执行这些写操作，本次不据此宣称全部历史页面的写权限已验收。

其他会话改动保留；未重启服务、未提交 Git、未发布模板。



## 真实浏览器内容节选

### DEPLOY_HARDWARE

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - alert:
      - generic: 安装地址已填写 0/1 台，所有设备均有安装地址后完成
    - generic: 序列号
    - textbox "序列号":
      - /placeholder: 查询设备序列号
    - generic: 产品编码
    - textbox "产品编码":
      - /placeholder: 查询产品编码
    - generic: 产品型号
    - textbox "产品型号":
      - /placeholder: 查询产品型号
    - button "刷新":
      - generic: 刷新
    - table:
      - rowgroup:
        - row "设备序列号 产品编码 产品型号 产品名称 安装地址 操作":
          - columnheader "设备序列号":
            - generic: 设备序列号
          - columnheader "产品编码":
            - generic: 产品编码
          - columnheader "产品型号":
            - generic: 产品型号
          - columnheader "产品名称":
            - generic: 产品名称
          - columnheader "安装地址":
            - generic: 安装地址
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup:
        - row "QA-SUPERVISION-64-20261015 QA-SUPERVISION-64 QA-VIRTUAL-FW 非实物验收样例 未填写 安装历史":
          - cell "QA-SUPERVISION-64-20261015":
            - generic: QA-SUPERVISION-64-20261015
          - cell "QA-SUPERVISION-64":
            - generic: QA-SUPERVISION-64
          - cell "QA-VIRTUAL-FW":
            - generic: QA-VIRTUAL-FW
          - cell "非实物验收样例":
            - generic: 非实物验收样例
          - cell "未填写":
            - generic: 未填写
          - cell "安装历史":
            - button "安装历史":
              - generic: 安装历史
    - combobox
    - generic: 20条/页
    - generic: 共 1 条
    - button "上一页" [disabled]:
    - list:
      - listitem "第 1 页": "1"
    - button "下一页" [disabled]:
    - generic: 前往
    - spinbutton "页": "1"
    - generic: 页
- generic: 交付件清单
- generic: 可选
- generic: 工程自检表
- generic: D_ENGINEERING_SELF_CHECK
- generic: 待提交
- button "提交与查看":
  - generic: 提交与查看
- generic: 可选
- generic: 机房实施承诺书
- generic: D_ROOM_COMMITMENT
- generic: 待提交
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。
- button "刷新业务结果":
  - generic: 刷新业务结果
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务


```

### DEPLOY_CONFIG

```text

- generic: 任务业务办理
- paragraph: 本任务业务在下方工作区直接办理，不跳离当前任务；办理完成后使用任务状态操作推进状态。
- generic: 状态
- combobox "状态"
- generic: 请选择
- button "查询":
  - text: 查询
- button "新增配置调试":
  - text: 新增配置调试
- button "上传日志 / 文件历史":
  - generic: 上传日志 / 文件历史
- alert:
  - generic: 支持手工日志上传与设备命令采集；采集日志自动回传到配置记录，调试完成仍由原业务动作确认。
- table:
  - rowgroup:
    - row "设备 调试时间 调试结果 状态 配置日志 操作":
      - columnheader "设备":
        - generic: 设备
      - columnheader "调试时间":
        - generic: 调试时间
      - columnheader "调试结果":
        - generic: 调试结果
      - columnheader "状态":
        - generic: 状态
      - columnheader "配置日志":
        - generic: 配置日志
      - columnheader "操作":
        - generic: 操作
- table:
  - rowgroup
- generic: 暂无数据
- generic: 交付件清单
- generic: 必选
- generic: 启用功能与配置Log
- generic: D_CONFIGURATION
- generic: 已验收
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。
- button "刷新工作区":
  - generic: 刷新工作区
- region "任务状态操作"


```

### DEPLOY_JOINT

```text

- generic: 任务业务办理
- paragraph: 本任务业务在下方工作区直接办理，不跳离当前任务；办理完成后使用任务状态操作推进状态。
- generic: 状态
- combobox "状态"
- generic: 请选择
- button "查询":
  - text: 查询
- button "新增联调":
  - text: 新增联调
- button "上传日志 / 文件历史":
  - generic: 上传日志 / 文件历史
- alert:
  - generic: 联调记录可直接输入命令或选用已发布模板采集设备日志；日志自动回传到联调记录，联调结果和通过操作仍按原流程确认。自动对比未接入。
- table:
  - rowgroup:
    - row "联调用例 设备编号 联调时间 状态 操作":
      - columnheader "联调用例":
        - generic: 联调用例
      - columnheader "设备编号":
        - generic: 设备编号
      - columnheader "联调时间":
        - generic: 联调时间
      - columnheader "状态":
        - generic: 状态
      - columnheader "操作":
        - generic: 操作
- table:
  - rowgroup
- generic: 暂无数据
- generic: 交付件清单
- generic: 必选
- generic: 运行业务及联调记录
- generic: D_JOINT
- generic: 已验收
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。
- button "刷新工作区":
  - generic: 刷新工作区
- region "任务状态操作"


```

### PROJECT_ASSIGNMENT

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - text: 项目成员
    - button "刷新":
      - text: 刷新
    - button "新增成员" [disabled]:
      - text: 新增成员
    - generic: 记录
    - radiogroup "成员记录":
      - radio "当前成员" [checked]
      - generic: 当前成员
      - radio "历史成员"
      - generic: 历史成员
    - generic: 角色
    - combobox "角色筛选"
    - generic: 全部角色
    - generic: 人员
    - textbox "成员姓名":
      - /placeholder: 按姓名查询
    - button "查询":
      - text: 查询
    - table:
      - rowgroup:
        - row "人员 单位 角色 联系电话 邮箱 主责 生效时间 失效时间 操作":
          - columnheader "人员":
            - generic: 人员
          - columnheader "单位":
            - generic: 单位
          - columnheader "角色":
            - generic: 角色
          - columnheader "联系电话":
            - generic: 联系电话
          - columnheader "邮箱":
            - generic: 邮箱
          - columnheader "主责":
            - generic: 主责
          - columnheader "生效时间":
            - generic: 生效时间
          - columnheader "失效时间":
            - generic: 失效时间
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup:
        - row "管理员 迪普科技示例公司 项目经理 18818260272 admin@npms.local 是 2026-10-15 23:44:54 — 编辑 移出项目":
          - cell "管理员":
            - generic: 管理员
          - cell "迪普科技示例公司":
            - generic: 迪普科技示例公司
          - cell "项目经理":
            - generic: 项目经理
          - cell "18818260272":
            - generic: "18818260272"
          - cell "admin@npms.local":
            - generic: admin@npms.local
          - cell "是":
            - generic: 是
          - cell "2026-10-15 23:44:54":
            - generic: 2026-10-15 23:44:54
          - cell "—":
            - generic: —
          - cell "编辑 移出项目":
            - button "编辑" [disabled]:
              - generic: 编辑
            - button "移出项目" [disabled]:
              - generic: 移出项目
        - row "管理员 — 服务经理 18818260272 admin@npms.local 是 2026-10-15 23:44:39 — 编辑 移出项目":
          - cell "管理员":
            - generic: 管理员
          - cell "—":
            - generic: —
          - cell "服务经理":
            - generic: 服务经理
          - cell "18818260272":
            - generic: "18818260272"
          - cell "admin@npms.local":
            - generic: admin@npms.local
          - cell "是":
            - generic: 是
          - cell "2026-10-15 23:44:39":
            - generic: 2026-10-15 23:44:39
          - cell "—":
            - generic: —
          - cell "编辑 移出项目":
            - button "编辑" [disabled]:
              - generic: 编辑
            - button "移出项目" [disabled]:
              - generic: 移出项目
        - row "项目64验收用户 隔离验收样例单位 最终用户 — project64.acceptance@example.test — 2026-10-15 23:45:08 — 编辑 移出项目":
          - cell "项目64验收用户":
            - generic: 项目64验收用户
          - cell "隔离验收样例单位":
            - generic: 隔离验收样例单位
          - cell "最终用户":
            - generic: 最终用户
          - cell "—":
            - generic: —
          - cell "project64.acceptance@example.test":
            - generic: project64.acceptance@example.test
          - cell "—":
            - generic: —
          - cell "2026-10-15 23:45:08":
            - generic: 2026-10-15 23:45:08
          - cell "—":
            - generic: —
          - cell "编辑 移出项目":
            - button "编辑" [disabled]:
              - generic: 编辑
            - button "移出项目" [disabled]:
              - generic: 移出项目
    - combobox
    - generic: 10条/页
    - generic: 共 3 条
    - button "上一页" [disabled]:
    - list:
      - listitem "第 1 页": "1"
    - button "下一页" [disabled]:
    - generic: 前往
    - spinbutton "页": "1"
    - generic: 页
    - generic: 项目已关闭，成员只读。
- button "刷新业务结果":
  - generic: 刷新业务结果
- region "任务状态操作"


```

### PRE_CONTACT

```text

- generic: 任务业务办理
- paragraph: 维护最终用户外部人员后，将按当前任务规则同步完成状态。
- text: 项目成员
- button "刷新":
  - text: 刷新
- button "新增成员" [disabled]:
  - text: 新增成员
- generic: 记录
- radiogroup "成员记录":
  - radio "当前成员" [checked]
  - generic: 当前成员
  - radio "历史成员"
  - generic: 历史成员
- generic: 角色
- combobox "角色筛选"
- generic: 全部角色
- generic: 人员
- textbox "成员姓名":
  - /placeholder: 按姓名查询
- button "查询":
  - text: 查询
- table:
  - rowgroup:
    - row "人员 单位 角色 联系电话 邮箱 主责 生效时间 失效时间 操作":
      - columnheader "人员":
        - generic: 人员
      - columnheader "单位":
        - generic: 单位
      - columnheader "角色":
        - generic: 角色
      - columnheader "联系电话":
        - generic: 联系电话
      - columnheader "邮箱":
        - generic: 邮箱
      - columnheader "主责":
        - generic: 主责
      - columnheader "生效时间":
        - generic: 生效时间
      - columnheader "失效时间":
        - generic: 失效时间
      - columnheader "操作":
        - generic: 操作
- table:
  - rowgroup:
    - row "管理员 迪普科技示例公司 项目经理 18818260272 admin@npms.local 是 2026-10-15 23:44:54 — 编辑 移出项目":
      - cell "管理员":
        - generic: 管理员
      - cell "迪普科技示例公司":
        - generic: 迪普科技示例公司
      - cell "项目经理":
        - generic: 项目经理
      - cell "18818260272":
        - generic: "18818260272"
      - cell "admin@npms.local":
        - generic: admin@npms.local
      - cell "是":
        - generic: 是
      - cell "2026-10-15 23:44:54":
        - generic: 2026-10-15 23:44:54
      - cell "—":
        - generic: —
      - cell "编辑 移出项目":
        - button "编辑" [disabled]:
          - generic: 编辑
        - button "移出项目" [disabled]:
          - generic: 移出项目
    - row "管理员 — 服务经理 18818260272 admin@npms.local 是 2026-10-15 23:44:39 — 编辑 移出项目":
      - cell "管理员":
        - generic: 管理员
      - cell "—":
        - generic: —
      - cell "服务经理":
        - generic: 服务经理
      - cell "18818260272":
        - generic: "18818260272"
      - cell "admin@npms.local":
        - generic: admin@npms.local
      - cell "是":
        - generic: 是
      - cell "2026-10-15 23:44:39":
        - generic: 2026-10-15 23:44:39
      - cell "—":
        - generic: —
      - cell "编辑 移出项目":
        - button "编辑" [disabled]:
          - generic: 编辑
        - button "移出项目" [disabled]:
          - generic: 移出项目
    - row "项目64验收用户 隔离验收样例单位 最终用户 — project64.acceptance@example.test — 2026-10-15 23:45:08 — 编辑 移出项目":
      - cell "项目64验收用户":
        - generic: 项目64验收用户
      - cell "隔离验收样例单位":
        - generic: 隔离验收样例单位
      - cell "最终用户":
        - generic: 最终用户
      - cell "—":
        - generic: —
      - cell "project64.acceptance@example.test":
        - generic: project64.acceptance@example.test
      - cell "—":
        - generic: —
      - cell "2026-10-15 23:45:08":
        - generic: 2026-10-15 23:45:08
      - cell "—":
        - generic: —
      - cell "编辑 移出项目":
        - button "编辑" [disabled]:
          - generic: 编辑
        - button "移出项目" [disabled]:
          - generic: 移出项目
- combobox
- generic: 10条/页
- generic: 共 3 条
- button "上一页" [disabled]:
- list:
  - listitem "第 1 页": "1"
- button "下一页" [disabled]:
- generic: 前往
- spinbutton "页": "1"
- generic: 页
- generic: 项目已关闭，成员只读。
- button "刷新工作区":
  - generic: 刷新工作区
- region "任务状态操作"


```

### PRE_SURVEY

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - generic: 项目编号
    - combobox "项目编号" [disabled]
    - generic: 督导六项按需任务至闭环验收20261015
    - generic: 工勘名称
    - textbox "工勘名称"
    - generic: 状态
    - combobox "状态"
    - generic: 请选择
    - button "查询":
      - text: 查询
    - table:
      - rowgroup:
        - row "工勘名称 工勘地点 地点状态 工勘日期 状态 操作":
          - columnheader "工勘名称":
            - generic: 工勘名称
          - columnheader "工勘地点":
            - generic: 工勘地点
          - columnheader "地点状态":
            - generic: 地点状态
          - columnheader "工勘日期":
            - generic: 工勘日期
          - columnheader "状态":
            - generic: 状态
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup
    - generic: 暂无数据
- button "刷新业务结果":
  - generic: 刷新业务结果
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务


```

### PRE_REQUIREMENT

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - strong: 需求分析
    - paragraph: 维护项目需求与附件，按版本保存和确认。
    - separator
    - generic "选择需求分析版本"
    - paragraph: 创建草稿后可填写11项核心内容及表单扩展项
- button "刷新业务结果":
  - generic: 刷新业务结果
- button "刷新":
  - text: 刷新
- button "修订记录" [disabled]:
  - generic: 修订记录
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务


```

### PLAN_CONSTRUCTION

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - heading "施工计划" [level=3]
    - paragraph: 从工期要求倒排阶段，在阶段内安排任务，确认后提交服务经理审核。
    - term: 签约方式
    - definition: 非直签
    - generic: 尚未关联合同
    - term: 工勘要求结束日期
    - definition: 未登记
    - generic: 来自工前准备
    - term: 当前生效工期
    - definition: 未录入
    - generic: 录入后可制定施工计划
    - term: 本版倒排截止日期
    - definition: 尚未推算
    - generic: 创建计划后读取权威输入
    - combobox "施工计划版本"
    - generic: 选择计划版本
    - paragraph: 先录入项目工期，再倒排阶段并安排任务。
- generic: 交付件清单
- generic: 必选
- generic: 经审批施工计划
- generic: D_CONSTRUCTION_PLAN
- generic: 待提交
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。
- button "刷新业务结果":
  - generic: 刷新业务结果
- button "刷新":
  - generic: 刷新
- button "录入工期":
  - generic: 录入工期
- button "制定施工计划" [disabled]:
  - generic: 制定施工计划
- button "录入项目工期":
  - generic: 录入项目工期
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务


```

### PLAN_SOLUTION

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - generic: 当前方案
    - combobox
    - generic: PJT2026000064_督导六项按需任务至闭环验收20261015_实施方案_V1（V1）
    - button "新增方案":
      - text: 新增方案
    - button "审批记录":
      - generic: 审批记录
    - generic: 1. 项目概述
    - group "1.1 项目背景":
      - generic: 1.1 项目背景
      - paragraph:
        - generic: 项目64本地隔离验收方案，仅使用虚拟设备与合成发货样例，不连接生产设备。
      - button "从 2.3 需求分析引用" [disabled]:
        - generic: 从 2.3 需求分析引用
    - group "1.2 项目目标":
      - generic: 1.2 项目目标
      - paragraph
      - generic: 请输入内容...
      - button "从 2.3 需求分析引用" [disabled]:
        - generic: 从 2.3 需求分析引用
    - group "1.3 项目团队":
      - generic: 1.3 项目团队
      - table:
        - rowgroup:
          - row "角色 姓名 联系方式 邮箱 备注 操作":
            - columnheader "角色":
              - generic: 角色
            - columnheader "姓名":
              - generic: 姓名
            - columnheader "联系方式":
              - generic: 联系方式
            - columnheader "邮箱":
              - generic: 邮箱
            - columnheader "备注":
              - generic: 备注
            - columnheader "操作":
              - generic: 操作
      - table:
        - rowgroup
      - generic: 暂无数据
      - button "添加成员" [disabled]:
        - generic: 添加成员
      - button "从项目成员与客户联系人引用" [disabled]:
        - generic: 从项目成员与客户联系人引用
    - group "1.4 项目清单":
      - generic: 1.4 项目清单
      - table:
        - rowgroup:
          - row "序号 产品编码 产品型号 产品描述 项目数量 发货数量 未发货数量 序列号 操作":
            - columnheader "序号":
              - generic: 序号
            - columnheader "产品编码":
              - generic: 产品编码
            - columnheader "产品型号":
              - generic: 产品型号
            - columnheader "产品描述":
              - generic: 产品描述
            - columnheader "项目数量":
              - generic: 项目数量
            - columnheader "发货数量":
              - generic: 发货数量
            - columnheader "未发货数量":
              - generic: 未发货数量
            - columnheader "序列号":
              - generic: 序列号
            - columnheader "操作":
              - generic: 操作
      - table:
        - rowgroup
      - generic: 暂无数据
      - button "添加行" [disabled]:
        - generic: 添加行
      - button "从 1.1 产品信息清单引用" [disabled]:
        - generic: 从 1.1 产品信息清单引用
    - generic: 2. 现网现状分析
    - group "2.1 现网拓扑图":
      - generic: 2.1 现网拓扑图
    - group "2.2 现网资源确认":
      - generic: 2.2 现网资源确认
      - paragraph
      - generic: 请输入内容...
      - button "引用工勘和需求分析" [disabled]:
        - generic: 引用工勘和需求分析
    - generic: 3. 总体方案设计
    - generic: 3.1 建设后拓扑
    - radiogroup "3.1 建设后拓扑":
      - radio "与需求分析无变化" [disabled]
      - generic: 与需求分析无变化
      - radio "有变化" [disabled]
      - generic: 有变化
    - group "3.2 部署位置规划":
      - generic: 3.2 部署位置规划
      - table:
        - rowgroup:
          - row "产品编码 设备名称 数量（订单数量） 安装位置（机房/机柜/U数） 备注":
            - columnheader "产品编码":
              - generic: 产品编码
            - columnheader "设备名称":
              - generic: 设备名称
            - columnheader "数量（订单数量）":
              - generic: 数量（订单数量）
            - columnheader "安装位置（机房/机柜/U数）":
              - generic: 安装位置（机房/机柜/U数）
            - columnheader "备注":
              - generic: 备注
      - table:
        - rowgroup
      - generic: 暂无数据
    - group "3.3 接口互联规划":
      - generic: 3.3 接口互联规划
      - table:
        - rowgroup:
          - row "本端设备 本端接口 对端接口 接口类型 线缆/光模块型号 备注 操作":
            - columnheader "本端设备":
              - generic: 本端设备
            - columnheader "本端接口":
              - generic: 本端接口
            - columnheader "对端接口":
              - generic: 对端接口
            - columnheader "接口类型":
              - generic: 接口类型
            - columnheader "线缆/光模块型号":
              - generic: 线缆/光模块型号
            - columnheader "备注":
              - generic: 备注
            - columnheader "操作":
              - generic: 操作
      - table:
        - rowgroup
      - generic: 暂无数据
      - button "添加行" [disabled]:
        - generic: 添加行
    - group "3.4 IP与vlan规划":
      - generic: 3.4 IP与vlan规划
      - table:
        - rowgroup:
          - row "设备名称 接口 IP地址 掩码 VRRP地址 网关 备注 操作":
            - columnheader "设备名称":
              - generic: 设备名称
            - columnheader "接口":
              - generic: 接口
            - columnheader "IP地址":
              - generic: IP地址
            - columnheader "掩码":
              - generic: 掩码
            - columnheader "VRRP地址":
              - generic: VRRP地址
            - columnheader "网关":
              - generic: 网关
            - columnheader "备注":
              - generic: 备注
            - columnheader "操作":
              - generic: 操作
      - table:
        - rowgroup
      - generic: 暂无数据
      - button "添加行" [disabled]:
        - generic: 添加行
    - group "3.5 软件版本":
      - generic: 3.5 软件版本
      - table:
        - rowgroup:
          - row "产品编码 设备名称 数量（订单数量） 软件版本 备注":
            - columnheader "产品编码":
              - generic: 产品编码
            - columnheader "设备名称":
              - generic: 设备名称
            - columnheader "数量（订单数量）":
              - generic: 数量（订单数量）
            - columnheader "软件版本":
              - generic: 软件版本
            - columnheader "备注":
              - generic: 备注
      - table:
        - rowgroup
      - generic: 暂无数据
    - generic: 4. 配置脚本
    - generic: 4.1 网络配置脚本
    - textbox "4.1 网络配置脚本" [disabled]:
      - /placeholder: 网络配置脚本
    - button "上传脚本" [disabled]:
      - button "上传脚本" [disabled]:
        - generic: 上传脚本
    - generic: 支持上传脚本文件，读取后自动展示在文本框中，可继续修改
    - generic: 4.2 业务模块功能配置脚本
    - textbox "4.2 业务模块功能配置脚本" [disabled]:
      - /placeholder: 业务模块功能配置脚本
    - button "上传脚本" [disabled]:
      - button "上传脚本" [disabled]:
        - generic: 上传脚本
    - generic: 支持上传脚本文件，读取后自动展示在文本框中，可继续修改
    - generic: 4.3 高可用配置脚本
    - textbox "4.3 高可用配置脚本" [disabled]:
      - /placeholder: 高可用配置脚本
    - button "上传脚本" [disabled]:
      - button "上传脚本" [disabled]:
        - generic: 上传脚本
    - generic: 支持上传脚本文件，读取后自动展示在文本框中，可继续修改
    - generic: 4.4 全量配置脚本
    - textbox "4.4 全量配置脚本" [disabled]:
      - /placeholder: 全量配置脚本
    - button "上传脚本" [disabled]:
      - button "上传脚本" [disabled]:
        - generic: 上传脚本
    - generic: 支持上传脚本文件，读取后自动展示在文本框中，可继续修改
    - generic: 5. 实施步骤
    - generic: 5.1 硬件实施
    - radiogroup "5.1 硬件实施":
      - radio "涉及硬件实施" [disabled]
      - generic: 涉及硬件实施
      - radio "不涉及" [checked] [disabled]
      - generic: 不涉及
    - generic: 5.2 软件调试
    - textbox "5.2 软件调试" [disabled]:
      - /placeholder: 软件调试步骤说明
      - text: 仅验证隔离环境业务操作、文档留存与审批，不下发配置。
    - group "5.3 业务配置":
      - generic: 5.3 业务配置
      - table:
        - rowgroup:
          - row "设备名称 序列号 承载业务名称 业务网段 业务重要等级 出入接口 客户侧负责人 备注 操作":
            - columnheader "设备名称":
              - generic: 设备名称
            - columnheader "序列号":
              - generic: 序列号
            - columnheader "承载业务名称":
              - generic: 承载业务名称
            - columnheader "业务网段":
              - generic: 业务网段
            - columnheader "业务重要等级":
              - generic: 业务重要等级
            - columnheader "出入接口":
              - generic: 出入接口
            - columnheader "客户侧负责人":
              - generic: 客户侧负责人
            - columnheader "备注":
              - generic: 备注
            - columnheader "操作":
              - generic: 操作
      - table:
        - rowgroup
      - generic: 暂无数据
      - button "添加行" [disabled]:
        - generic: 添加行
      - button "从 2.3 需求分析引用" [disabled]:
        - generic: 从 2.3 需求分析引用
    - generic: 6. 可选方案模块
    - group "checkbox-group":
      - checkbox "6.1 质量保障方案" [disabled]
      - generic: 6.1 质量保障方案
      - checkbox "6.2 风险管控与应急预案" [disabled]
      - generic: 6.2 风险管控与应急预案
      - checkbox "6.3 运维交付与售后服务" [disabled]
      - generic: 6.3 运维交付与售后服务
      - checkbox "6.4 项目问题闭环与文档归档" [disabled]
      - generic: 6.4 项目问题闭环与文档归档
    - generic: 7. 项目培训及资料移交
    - generic: 7.1 培训目的
    - textbox "7.1 培训目的" [disabled]:
      - /placeholder: 按照实际情况填写
    - generic: 7.2 培训内容
    - textbox "7.2 培训内容" [disabled]
    - generic: 模板要点：项目整体架构讲解 / 设备基础操作 / 日常运维与巡检 / 业务配置实操 / 常见故障处理 / 安全规范与账号管理
    - generic: 8. 售后服务
    - heading "8.1 400热线" [level=4]
    - paragraph: 迪普科技设有7×24小时客户服务热线，受理故障申报与技术咨询。客户服务热线：400-610-0598。
    - heading "8.2 现场服务" [level=4]
    - paragraph: 重大故障按服务等级约定安排工程师现场支持。
    - heading "8.3 软件更新" [level=4]
    - paragraph: 在维保期内提供软件版本升级与补丁服务，License 授权变更按合同约定执行。
    - heading "8.4 网站论坛" [level=4]
    - paragraph: 官网：http://www.dptech.com，提供产品资料与知识库查询。
    - generic: 交付件（统一交付能力）
    - button "刷新" [disabled]:
      - generic: 刷新
    - generic: 材料类型
    - combobox "材料类型"
    - generic: 选择类型
    - generic: 材料标题
    - textbox "材料标题":
      - /placeholder: 可选
    - heading "交付材料" [level=4]
    - table:
      - rowgroup:
        - row "选择所有行 类型 标题 文件或业务成果 版本 来源 状态 文件访问 操作":
          - columnheader "选择所有行":
            - generic "选择所有行":
              - checkbox "选择所有行"
          - columnheader "类型":
            - generic: 类型
          - columnheader "标题":
            - generic: 标题
          - columnheader "文件或业务成果":
            - generic: 文件或业务成果
          - columnheader "版本":
            - generic: 版本
          - columnheader "来源":
            - generic: 来源
          - columnheader "状态":
            - generic: 状态
          - columnheader "文件访问":
            - generic: 文件访问
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup:
        - row "选择当前行 IMPLEMENTATION_PLAN PJT2026000064_督导六项按需任务至闭环验收20261015_实施方案_V1（基线v4） solution · 30044 业务修订 4 ASSOCIATED 有效 撤回":
          - cell "选择当前行":
            - generic "选择当前行":
              - checkbox "选择当前行"
          - cell "IMPLEMENTATION_PLAN":
            - generic: IMPLEMENTATION_PLAN
          - cell "PJT2026000064_督导六项按需任务至闭环验收20261015_实施方案_V1（基线v4）":
            - generic: PJT2026000064_督导六项按需任务至闭环验收20261015_实施方案_V1（基线v4）
          - cell "solution · 30044":
            - generic: solution · 30044
          - cell "业务修订 4":
            - generic: 业务修订 4
          - cell "ASSOCIATED":
            - generic: ASSOCIATED
          - cell "有效":
            - generic: 有效
          - cell
          - cell "撤回":
            - button "撤回" [disabled]:
              - generic: 撤回
    - heading "交付要求" [level=4]
    - table:
      - rowgroup:
        - row "类型 性质 计数口径 数量 状态 当前提交 操作":
          - columnheader "类型":
            - generic: 类型
          - columnheader "性质":
            - generic: 性质
          - columnheader "计数口径":
            - generic: 计数口径
          - columnheader "数量":
            - generic: 数量
          - columnheader "状态":
            - generic: 状态
          - columnheader "当前提交":
            - generic: 当前提交
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup
    - generic: 暂无数据
- button "刷新业务结果":
  - generic: 刷新业务结果
- button "下载方案" [disabled]:
  - generic: 下载方案
- region "任务状态操作"


```

### ACCEPT_TRAINING

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - heading "现场培训" [level=2]
    - paragraph: 记录培训内容，邀请客户评价并签字确认。
    - generic: 名称
    - textbox "名称"
    - generic: 状态
    - combobox "状态"
    - generic: 请选择
    - button "打印模板配置":
      - generic: 打印模板配置
    - button "查询":
      - text: 查询
    - table:
      - rowgroup:
        - row "培训名称 培训类型 培训时间 培训工程师 客户联系人 状态 客户确认 操作":
          - columnheader "培训名称":
            - generic: 培训名称
          - columnheader "培训类型":
            - generic: 培训类型
          - columnheader "培训时间":
            - generic: 培训时间
          - columnheader "培训工程师":
            - generic: 培训工程师
          - columnheader "客户联系人":
            - generic: 客户联系人
          - columnheader "状态":
            - generic: 状态
          - columnheader "客户确认":
            - generic: 客户确认
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup
    - generic: 暂无数据
- button "刷新业务结果":
  - generic: 刷新业务结果
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务


```

### ACCEPT_SATISFACTION

```text

- generic: 交付件清单
- generic: 必选
- generic: 满意度调查有效结果
- generic: D_SATISFACTION
- generic: 待提交
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。


```

### DEPLOY_CUTOVER

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - strong: 割接任务 0
    - button "刷新":
      - generic: 刷新
    - table:
      - rowgroup:
        - row "任务编号 任务名称 来源 等级 当前步骤 状态 生成时间 操作":
          - columnheader "任务编号":
            - generic: 任务编号
          - columnheader "任务名称":
            - generic: 任务名称
          - columnheader "来源":
            - generic: 来源
          - columnheader "等级":
            - generic: 等级
          - columnheader "当前步骤":
            - generic: 当前步骤
          - columnheader "状态":
            - generic: 状态
          - columnheader "生成时间":
            - generic: 生成时间
          - columnheader "操作":
            - generic: 操作
    - table:
      - rowgroup
    - generic: 当前项目暂无割接任务
- button "刷新业务结果":
  - generic: 刷新业务结果
- region "任务状态操作":
  - button "关闭任务":
    - generic: 关闭任务
  - region "任务资料与进度":
    - button "编辑任务资料":
      - generic: 编辑任务资料


```

### PROJECT_CLOSURE

```text

- generic: 任务业务办理
- paragraph: 本任务在正常闭环审批通过后完成。请在下方校验条件并提交审批。
- generic "项目正常闭环":
  - heading "项目正常闭环" [level=3]
  - paragraph: 按项目冻结条件校验，经过真实审批后收口；不会补造阶段或验收结果。
  - button "刷新":
    - generic: 刷新
  - table:
    - rowgroup:
      - row "当前真实阶段 S1 项目状态 进行中":
        - cell "当前真实阶段"
        - cell "S1"
        - cell "项目状态"
        - cell "进行中"
  - list:
    - generic: 满足
    - generic: 已冻结本项目闭环规则
    - generic: 满足
    - generic: 项目处于进行中
    - generic: 待处理
    - generic: 尚未到达模板最后实际阶段
    - generic: 待处理
    - generic: UNSATISFIED
    - generic: 待处理
    - generic: 仍有未完成任务
    - generic: 待处理
    - generic: 请重新校验并生成闭环快照
- button "刷新工作区":
  - generic: 刷新工作区


```

### NATIVE_DELIVERABLE_DIALOG

```text

- generic: 交付件清单
- generic: 必选
- generic: 满意度调查有效结果
- generic: D_SATISFACTION
- generic: 待提交
- button "提交与查看":
  - generic: 提交与查看
- paragraph: 文件或业务成果有效且模板条件满足时，自动满足交付要求。
- dialog "交付件提交与判定":
  - heading "交付件提交与判定" [level=2]
  - button "关闭此对话框":
  - heading "满意度调查有效结果" [level=3]
  - paragraph: 状态：待满足 · 至少 1 项有效材料
  - paragraph: 提交后按模板条件自动判定。文件或成果有效且配置条件满足时，交付件满足门禁。
  - alert:
    - generic: 当前交付件只读：请确认项目仍在进行中，且具有该项目的材料管理权限。
  - alert:
    - generic: 业务页面上传的匹配文档会自动归集；归集完成后刷新查看，无需重复上传。
  - separator:
    - generic: 提交历史
  - paragraph: 尚无提交记录
  - button "关闭":
    - generic: 关闭


```

### CUTOVER_APPROVAL_HISTORY

```text

- generic: 任务业务办理
- region "任务业务办理":
  - region "业务视图":
    - region "项目执行规则":
      - generic "操作权限与规则":
        - table:
          - rowgroup:
            - row "业务操作 业务权限 项目执行 前置判定 阻塞原因":
              - columnheader "业务操作":
                - generic: 业务操作
              - columnheader "业务权限":
                - generic: 业务权限
              - columnheader "项目执行":
                - generic: 项目执行
              - columnheader "前置判定":
                - generic: 前置判定
              - columnheader "阻塞原因":
                - generic: 阻塞原因
        - table:
          - rowgroup:
            - row "发起项目割接 未获授权 不可办理 NO_ADDITIONAL_RULE OWNER_OPERATION_FORBIDDEN":
              - cell "发起项目割接":
                - generic: 发起项目割接
              - cell "未获授权":
                - generic: 未获授权
              - cell "不可办理":
                - generic: 不可办理
              - cell "NO_ADDITIONAL_RULE":
                - generic: NO_ADDITIONAL_RULE
              - cell "OWNER_OPERATION_FORBIDDEN":
                - generic: OWNER_OPERATION_FORBIDDEN
    - button "返回任务列表":
      - text: 返回任务列表
    - strong: 督导六项按需任务至闭环验收20261015-割接-CUT-2110786024697167872
    - generic: CUT-2110786024697167872
    - generic: 已归档
    - button "刷新":
      - generic: 刷新
    - navigation "割接办理流程":
      - list:
        - button "割接等级确认":
          - generic: ✓
          - generic: 割接等级确认
        - button "调研及风险考察":
          - generic: ✓
          - generic: 调研及风险考察
        - button "割接方案":
          - generic: ✓
          - generic: 割接方案
        - button "割接审批" [active]:
          - generic: ✓
          - generic: 割接审批
        - button "割接跟踪及闭环":
          - generic: ✓
          - generic: 割接跟踪及闭环
    - region "项目信息":
      - alert:
        - generic: 审批页面引用方案提交时的固定版本。审批人根据项目、调研、风险、等级及方案进行五项合理性评审。
      - heading "项目信息" [level=3]:
        - text: 项目信息
      - generic: 引用割接等级确认时的项目信息
      - generic: 项目名称
      - generic: 督导六项按需任务至闭环验收20261015
      - generic: 项目编号
      - generic: PJT2026000064
      - generic: 设备序列号
      - generic: QA-SUPERVISION-64-20261015
      - generic: 办事处
      - generic: 杭州服务办事处
      - heading "采集信息分析" [level=3]:
        - text: 采集信息分析
      - generic: 引用调研及风险考察
      - generic: 割接类型
      - generic: DEVICE_ONBOARD
      - generic: 高可靠性组网模式
      - generic: 未配置
      - generic: 设备类型
      - generic: —
      - generic: 割接操作时间
      - generic: 2026/10/17 00:00
      - heading "风险考察项" [level=4]
      - table:
        - rowgroup:
          - row "调研／风险项 结果 事实说明":
            - columnheader "调研／风险项":
              - generic: 调研／风险项
            - columnheader "结果":
              - generic: 结果
            - columnheader "事实说明":
              - generic: 事实说明
      - table:
        - rowgroup:
          - row "配置文件备份 附件 已提交附件（第 1 版） configuration.pdf 已绑定 V1 版本历史 预览 下载 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
            - cell "配置文件备份":
              - generic: 配置文件备份
            - cell "附件 已提交附件（第 1 版） configuration.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "configuration.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
              - generic: 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。
          - row "技术公告检查项－当前版本 是否有相关技术公告 否 版本与查询依据 2026-10-16核查自建本地模拟器QA-SUPERVISION-1.0源说明与本轮日志，仅合成设备无厂商技术公告；不声称完成生产公告检索。 技术公告 —":
            - cell "技术公告检查项－当前版本":
              - generic: 技术公告检查项－当前版本
            - cell "是否有相关技术公告 否 版本与查询依据 2026-10-16核查自建本地模拟器QA-SUPERVISION-1.0源说明与本轮日志，仅合成设备无厂商技术公告；不声称完成生产公告检索。 技术公告":
              - term: 是否有相关技术公告
              - definition: 否
              - term: 版本与查询依据
              - definition: 2026-10-16核查自建本地模拟器QA-SUPERVISION-1.0源说明与本轮日志，仅合成设备无厂商技术公告；不声称完成生产公告检索。
              - term: 技术公告
              - definition:
                - list
            - cell "—":
              - generic: —
          - row "诊断日志告警检查 normal 是 —":
            - cell "诊断日志告警检查":
              - generic: 诊断日志告警检查
            - cell "normal 是":
              - term: normal
              - definition: 是
            - cell "—":
              - generic: —
          - row "热补丁备份 附件 已提交附件（第 1 版） hotfix.pdf 已绑定 V1 版本历史 预览 下载 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
            - cell "热补丁备份":
              - generic: 热补丁备份
            - cell "附件 已提交附件（第 1 版） hotfix.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "hotfix.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
              - generic: 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。
          - row "License授权文件备份 附件 已提交附件（第 1 版） license.pdf 已绑定 V1 版本历史 预览 下载 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
            - cell "License授权文件备份":
              - generic: License授权文件备份
            - cell "附件 已提交附件（第 1 版） license.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "license.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
              - generic: 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。
          - row "机房操作承诺书 附件 已提交附件（第 1 版） room-commitment.pdf 已绑定 V1 版本历史 预览 下载 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
            - cell "机房操作承诺书":
              - generic: 机房操作承诺书
            - cell "附件 已提交附件（第 1 版） room-commitment.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "room-commitment.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。":
              - generic: 当前第2轮本地隔离流程验收样例，不代表生产资料或真实人员承诺。
          - row "运行版本备份 附件 已提交附件（第 1 版） runtime-version.pdf 已绑定 V1 版本历史 预览 下载 本地虚拟设备QA-SUPERVISION-1.0样例，无生产固件备份。":
            - cell "运行版本备份":
              - generic: 运行版本备份
            - cell "附件 已提交附件（第 1 版） runtime-version.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "runtime-version.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "本地虚拟设备QA-SUPERVISION-1.0样例，无生产固件备份。":
              - generic: 本地虚拟设备QA-SUPERVISION-1.0样例，无生产固件备份。
          - row "系统日志告警检查 normal 是 —":
            - cell "系统日志告警检查":
              - generic: 系统日志告警检查
            - cell "normal 是":
              - term: normal
              - definition: 是
            - cell "—":
              - generic: —
      - heading "业务调研项" [level=4]
      - table:
        - rowgroup:
          - row "调研／风险项 结果 事实说明":
            - columnheader "调研／风险项":
              - generic: 调研／风险项
            - columnheader "结果":
              - generic: 结果
            - columnheader "事实说明":
              - generic: 事实说明
      - table:
        - rowgroup:
          - row "涉及业务简述 仅验证非直签原厂督导项目从S4至S5及项目闭环的正式办理路径，六项按需任务保持未完成。 —":
            - cell "涉及业务简述":
              - generic: 涉及业务简述
            - cell "仅验证非直签原厂督导项目从S4至S5及项目闭环的正式办理路径，六项按需任务保持未完成。":
              - generic: 仅验证非直签原厂督导项目从S4至S5及项目闭环的正式办理路径，六项按需任务保持未完成。
            - cell "—":
              - generic: —
          - row "业务连通性测试例核验 附件 已提交附件（第 1 版） connectivity.pdf 已绑定 V1 版本历史 预览 下载 第2执行轮隔离验收样例；仅用于本地流程，不代表生产业务测试。":
            - cell "业务连通性测试例核验":
              - generic: 业务连通性测试例核验
            - cell "附件 已提交附件（第 1 版） connectivity.pdf 已绑定 V1 版本历史 预览 下载":
              - term: 附件
              - definition: 已提交附件（第 1 版）
              - generic "connectivity.pdf"
              - generic: 已绑定
              - generic: V1
              - button "版本历史":
                - generic: 版本历史
              - button "预览":
                - generic: 预览
              - button "下载":
                - generic: 下载
            - cell "第2执行轮隔离验收样例；仅用于本地流程，不代表生产业务测试。":
              - generic: 第2执行轮隔离验收样例；仅用于本地流程，不代表生产业务测试。
          - row "割接背景 是否解决网上问题 否 是否再次割接 是 上次割接负责人 管理员（隔离验收操作者）；原单样例保障人员见归档单CUT-2110768684412661760，不代表实际人员派工。 割接背景表述 项目64返工后第2执行轮隔离验收，旧割接单归档保留，本单重新采集。设备、接口、IP、性能均为合成样例；不连接生产设备、不执行生产变更。日志仅证明本地模拟器响应正常。 —":
            - cell "割接背景":
              - generic: 割接背景
            - cell "是否解决网上问题 否 是否再次割接 是 上次割接负责人 管理员（隔离验收操作者）；原单样例保障人员见归档单CUT-2110768684412661760，不代表实际人员派工。 割接背景表述 项目64返工后第2执行轮隔离验收，旧割接单归档保留，本单重新采集。设备、接口、IP、性能均为合成样例；不连接生产设备、不执行生产变更。日志仅证明本地模拟器响应正常。":
              - term: 是否解决网上问题
              - definition: 否
              - term: 是否再次割接
              - definition: 是
              - term: 上次割接负责人
              - definition: 管理员（隔离验收操作者）；原单样例保障人员见归档单CUT-2110768684412661760，不代表实际人员派工。
              - term: 割接背景表述
              - definition: 项目64返工后第2执行轮隔离验收，旧割接单归档保留，本单重新采集。设备、接口、IP、性能均为合成样例；不连接生产设备、不执行生产变更。日志仅证明本地模拟器响应正常。
            - cell "—":
              - generic: —
          - row "设备部署位置规划 设备类型 防火墙（虚拟样例） 序列号 QA-SUPERVISION-64-20261015 设备名称 督导闭环验收虚拟设备 部署位置 本机隔离模拟器，无实体机房 —":
            - cell "设备部署位置规划":
              - generic: 设备部署位置规划
            - cell "设备类型 防火墙（虚拟样例） 序列号 QA-SUPERVISION-64-20261015 设备名称 督导闭环验收虚拟设备 部署位置 本机隔离模拟器，无实体机房":
              - list:
                - term: 设备类型
                - definition: 防火墙（虚拟样例）
                - term: 序列号
                - definition: QA-SUPERVISION-64-20261015
                - term: 设备名称
                - definition: 督导闭环验收虚拟设备
                - term: 部署位置
                - definition: 本机隔离模拟器，无实体机房
            - cell "—":
              - generic: —
          - row "设备接口互联规划 本端设备 QA-VIRTUAL-FW 本端接口 mock0 对端接口 mock-peer0 接口类型 虚拟以太网 线缆型号 无实体线缆（合成） 备注 仅样例，无生产互联 —":
            - cell "设备接口互联规划":
              - generic: 设备接口互联规划
            - cell "本端设备 QA-VIRTUAL-FW 本端接口 mock0 对端接口 mock-peer0 接口类型 虚拟以太网 线缆型号 无实体线缆（合成） 备注 仅样例，无生产互联":
              - list:
                - term: 本端设备
                - definition: QA-VIRTUAL-FW
                - term: 本端接口
                - definition: mock0
                - term: 对端接口
                - definition: mock-peer0
                - term: 接口类型
                - definition: 虚拟以太网
                - term: 线缆型号
                - definition: 无实体线缆（合成）
                - term: 备注
                - definition: 仅样例，无生产互联
            - cell "—":
              - generic: —
          - row "设备IP地址与VLAN规划 设备名称 QA-VIRTUAL-FW 接口 mock0 IP地址 192.0.2.10 子网掩码 255.255.255.0 网关 192.0.2.1 备注 文档保留地址，仅表单样例 —":
            - cell "设备IP地址与VLAN规划":
              - generic: 设备IP地址与VLAN规划
            - cell "设备名称 QA-VIRTUAL-FW 接口 mock0 IP地址 192.0.2.10 子网掩码 255.255.255.0 网关 192.0.2.1 备注 文档保留地址，仅表单样例":
              - list:
                - term: 设备名称
                - definition: QA-VIRTUAL-FW
                - term: 接口
                - definition: mock0
                - term: IP地址
                - definition: 192.0.2.10
                - term: 子网掩码
                - definition: 255.255.255.0
                - term: 网关
                - definition: 192.0.2.1
                - term: 备注
                - definition: 文档保留地址，仅表单样例
            - cell "—":
              - generic: —
          - row "设备性能基线 序列号 QA-SUPERVISION-64-20261015 并发连接数 0 新建连接数 0 CPU使用率 0 内存使用率 0 —":
            - cell "设备性能基线":
              - generic: 设备性能基线
            - cell "序列号 QA-SUPERVISION-64-20261015 并发连接数 0 新建连接数 0 CPU使用率 0 内存使用率 0":
              - list:
                - term: 序列号
                - definition: QA-SUPERVISION-64-20261015
                - term: 并发连接数
                - definition: "0"
                - term: 新建连接数
                - definition: "0"
                - term: CPU使用率
                - definition: "0"
                - term: 内存使用率
                - definition: "0"
            - cell "—":
              - generic: —
      - heading "割接等级" [level=3]:
        - text: 割接等级
      - generic: 引用割接等级确认
      - generic: 业务重要等级
      - generic: 低
      - generic: 操作复杂度等级
      - generic: 低
      - generic: 隐患风险等级
      - generic: 低
      - generic: 备件是否申请
      - generic: 否
      - generic: 客户服务等级
      - generic: 人工确定割接等级
      - generic: C级
      - heading "割接方案" [level=3]:
        - text: 割接方案
      - generic: 第 1 版
      - heading "一、基本信息部分" [level=4]
      - generic: 项目与割接概述
      - textbox "项目与割接概述" [disabled]: 项目64第2执行轮隔离验收。旧轮割接已归档并保留；本任务重新办理当前轮次，仅限QA虚拟设备和合成数据，不执行生产命令。
      - heading "项目割接计划" [level=4]
      - table:
        - rowgroup:
          - row "日期 工作内容":
            - columnheader "日期":
              - generic: 日期
            - columnheader "工作内容":
              - generic: 工作内容
      - table:
        - rowgroup:
          - row "2026-10-16 01:57:18 第2轮隔离割接流程验收：补采、方案、审批、闭环及项目状态核对；不连接真实设备。":
            - cell "2026-10-16 01:57:18":
              - combobox [disabled]: 2026-10-16 01:57:18
            - cell "第2轮隔离割接流程验收：补采、方案、审批、闭环及项目状态核对；不连接真实设备。":
              - textbox [disabled]: 第2轮隔离割接流程验收：补采、方案、审批、闭环及项目状态核对；不连接真实设备。
      - heading "网络拓扑与组网配置" [level=4]
      - generic: 割接前拓扑
      - paragraph: 未上传
      - generic: 割接后拓扑
      - paragraph: 未上传
      - generic: 组网配置
      - paragraph: 未上传
      - heading "割接设备清单" [level=4]
      - table:
        - rowgroup:
          - row "涉及产品型号 在网软件版本 升级版本信息 备注":
            - columnheader "涉及产品型号":
              - generic: 涉及产品型号
            - columnheader "在网软件版本":
              - generic: 在网软件版本
            - columnheader "升级版本信息":
              - generic: 升级版本信息
            - columnheader "备注":
              - generic: 备注
      - table:
        - rowgroup:
          - row "QA-VIRTUAL-FW QA-SUPERVISION-64-20261015 QA-SUPERVISION-1.0 不升级（隔离流程验收） 仅本机模拟器，非生产设备":
            - cell "QA-VIRTUAL-FW QA-SUPERVISION-64-20261015":
              - textbox [disabled]: QA-VIRTUAL-FW
              - text: QA-SUPERVISION-64-20261015
            - cell "QA-SUPERVISION-1.0":
              - textbox [disabled]: QA-SUPERVISION-1.0
            - cell "不升级（隔离流程验收）":
              - textbox [disabled]: 不升级（隔离流程验收）
            - cell "仅本机模拟器，非生产设备":
              - textbox [disabled]: 仅本机模拟器，非生产设备
      - heading "割接保障人员安排" [level=4]
      - region "割接保障人员安排":
        - table:
          - rowgroup:
            - row "角色 姓名 任务描述 联系电话 到位时间":
              - columnheader "角色":
                - generic: 角色
              - columnheader "姓名":
                - generic: 姓名
              - columnheader "任务描述":
                - generic: 任务描述
              - columnheader "联系电话":
                - generic: 联系电话
              - columnheader "到位时间":
                - generic: 到位时间
        - table:
          - rowgroup:
            - row "客户 验收样例客户64（非真实人员） 仅隔离验收客户角色占位，不联系或代表真实客户。 00000000000 2026-10-16 01:57:00":
              - cell "客户":
                - combobox "保障角色" [disabled]
                - generic: 客户
              - cell "验收样例客户64（非真实人员）":
                - textbox [disabled]: 验收样例客户64（非真实人员）
              - cell "仅隔离验收客户角色占位，不联系或代表真实客户。":
                - textbox [disabled]: 仅隔离验收客户角色占位，不联系或代表真实客户。
              - cell "00000000000":
                - textbox [disabled]: "00000000000"
              - cell "2026-10-16 01:57:00":
                - combobox [disabled]: 2026-10-16 01:57:00
            - row "迪普一线工程师 验收样例一线64（非真实人员） 隔离验收角色安排样例，不代表真实人员派工或通知。 00000000001 2026-10-16 01:57:00":
              - cell "迪普一线工程师":
                - combobox "保障角色" [disabled]
                - generic: 迪普一线工程师
              - cell "验收样例一线64（非真实人员）":
                - textbox [disabled]: 验收样例一线64（非真实人员）
              - cell "隔离验收角色安排样例，不代表真实人员派工或通知。":
                - textbox [disabled]: 隔离验收角色安排样例，不代表真实人员派工或通知。
              - cell "00000000001":
                - textbox [disabled]: "00000000001"
              - cell "2026-10-16 01:57:00":
                - combobox [disabled]: 2026-10-16 01:57:00
            - row "迪普二线工程师 验收样例二线64（非真实人员） 隔离验收角色安排样例，不代表真实人员派工或通知。 00000000002 2026-10-16 01:57:00":
              - cell "迪普二线工程师":
                - combobox "保障角色" [disabled]
                - generic: 迪普二线工程师
              - cell "验收样例二线64（非真实人员）":
                - textbox [disabled]: 验收样例二线64（非真实人员）
              - cell "隔离验收角色安排样例，不代表真实人员派工或通知。":
                - textbox [disabled]: 隔离验收角色安排样例，不代表真实人员派工或通知。
              - cell "00000000002":
                - textbox [disabled]: "00000000002"
              - cell "2026-10-16 01:57:00":
                - combobox [disabled]: 2026-10-16 01:57:00
            - row "迪普研发 验收样例研发64（非真实人员） 隔离验收角色安排样例，不代表真实人员派工或通知。 00000000003 2026-10-16 01:57:00":
              - cell "迪普研发":
                - combobox "保障角色" [disabled]
                - generic: 迪普研发
              - cell "验收样例研发64（非真实人员）":
                - textbox [disabled]: 验收样例研发64（非真实人员）
              - cell "隔离验收角色安排样例，不代表真实人员派工或通知。":
                - textbox [disabled]: 隔离验收角色安排样例，不代表真实人员派工或通知。
              - cell "00000000003":
                - textbox [disabled]: "00000000003"
              - cell "2026-10-16 01:57:00":
                - combobox [disabled]: 2026-10-16 01:57:00
      - heading "二、执行操作部分" [level=4]
      - heading "割接前操作清单" [level=4]
      - table:
        - rowgroup:
          - row "序号 操作项目 测试结果 责任人":
            - columnheader "序号":
              - generic: 序号
            - columnheader "操作项目":
              - generic: 操作项目
            - columnheader "测试结果":
              - generic: 测试结果
            - columnheader "责任人":
              - generic: 责任人
      - table:
        - rowgroup:
          - row "1 核对本轮系统与诊断日志、六份样例附件和设备标识，仅使用本地隔离端点。 两项采集已回传本单；原始日志明确为SYNTHETIC，告警计数0仅是模拟值。 管理员（隔离验收）":
            - cell "1":
              - generic: "1"
            - cell "核对本轮系统与诊断日志、六份样例附件和设备标识，仅使用本地隔离端点。":
              - textbox [disabled]: 核对本轮系统与诊断日志、六份样例附件和设备标识，仅使用本地隔离端点。
            - cell "两项采集已回传本单；原始日志明确为SYNTHETIC，告警计数0仅是模拟值。":
              - textbox [disabled]: 两项采集已回传本单；原始日志明确为SYNTHETIC，告警计数0仅是模拟值。
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
      - heading "割接执行操作清单" [level=4]
      - table:
        - rowgroup:
          - row "序号 割接步骤 操作内容 业务影响时间 实施人员 操作时间":
            - columnheader "序号":
              - generic: 序号
            - columnheader "割接步骤":
              - generic: 割接步骤
            - columnheader "操作内容":
              - generic: 操作内容
            - columnheader "业务影响时间":
              - generic: 业务影响时间
            - columnheader "实施人员":
              - generic: 实施人员
            - columnheader "操作时间":
              - generic: 操作时间
      - table:
        - rowgroup:
          - row "1 隔离流程演练 通过正式页面提交当前轮次方案、审批并核对业务状态；不执行生产命令或设备配置写入。 0（不涉及生产业务） 管理员（隔离验收） 2026-10-16 本轮验收期间":
            - cell "1":
              - generic: "1"
            - cell "隔离流程演练":
              - textbox [disabled]: 隔离流程演练
            - cell "通过正式页面提交当前轮次方案、审批并核对业务状态；不执行生产命令或设备配置写入。":
              - textbox [disabled]: 通过正式页面提交当前轮次方案、审批并核对业务状态；不执行生产命令或设备配置写入。
            - cell "0（不涉及生产业务）":
              - textbox [disabled]: 0（不涉及生产业务）
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
            - cell "2026-10-16 本轮验收期间":
              - textbox [disabled]: 2026-10-16 本轮验收期间
      - heading "割接收尾收集清单" [level=4]
      - table:
        - rowgroup:
          - row "序号 操作项目 测试结果 责任人":
            - columnheader "序号":
              - generic: 序号
            - columnheader "操作项目":
              - generic: 操作项目
            - columnheader "测试结果":
              - generic: 测试结果
            - columnheader "责任人":
              - generic: 责任人
      - table:
        - rowgroup:
          - row "1 核对当前轮次日志回传、方案冻结版本和闭环附件，保留旧单历史。 按正式提交后的页面与回执检查，不以HTTP成功代替业务完成。 管理员（隔离验收）":
            - cell "1":
              - generic: "1"
            - cell "核对当前轮次日志回传、方案冻结版本和闭环附件，保留旧单历史。":
              - textbox [disabled]: 核对当前轮次日志回传、方案冻结版本和闭环附件，保留旧单历史。
            - cell "按正式提交后的页面与回执检查，不以HTTP成功代替业务完成。":
              - textbox [disabled]: 按正式提交后的页面与回执检查，不以HTTP成功代替业务完成。
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
      - heading "割接后业务测试表" [level=4]
      - table:
        - rowgroup:
          - row "序号 操作项目 测试结果 责任人":
            - columnheader "序号":
              - generic: 序号
            - columnheader "操作项目":
              - generic: 操作项目
            - columnheader "测试结果":
              - generic: 测试结果
            - columnheader "责任人":
              - generic: 责任人
      - table:
        - rowgroup:
          - row "1 检查S4当前任务关联、S5准入及项目闭环；六项按需任务保持未完成。 待本轮审批与闭环完成后核验，不提前认定项目验收通过。 管理员（隔离验收）":
            - cell "1":
              - generic: "1"
            - cell "检查S4当前任务关联、S5准入及项目闭环；六项按需任务保持未完成。":
              - textbox [disabled]: 检查S4当前任务关联、S5准入及项目闭环；六项按需任务保持未完成。
            - cell "待本轮审批与闭环完成后核验，不提前认定项目验收通过。":
              - textbox [disabled]: 待本轮审批与闭环完成后核验，不提前认定项目验收通过。
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
      - heading "回退方案说明" [level=4]
      - textbox [disabled]: 如隔离验收失败，停止提交并保留日志、草稿、历史；通过正式更正或返工入口处理，不直接改库、不覆盖不可变快照。无生产设备配置需要回退。
      - heading "回退方案操作步骤" [level=4]
      - table:
        - rowgroup:
          - row "序号 回退步骤 操作内容 实施人员 操作时间":
            - columnheader "序号":
              - generic: 序号
            - columnheader "回退步骤":
              - generic: 回退步骤
            - columnheader "操作内容":
              - generic: 操作内容
            - columnheader "实施人员":
              - generic: 实施人员
            - columnheader "操作时间":
              - generic: 操作时间
      - table:
        - rowgroup:
          - row "1 保留现场并停止流程 记录失败原因，保留当前单据与日志，通过正式业务入口修正；不删除旧轮证据。 管理员（隔离验收） 发现失败时":
            - cell "1":
              - generic: "1"
            - cell "保留现场并停止流程":
              - textbox [disabled]: 保留现场并停止流程
            - cell "记录失败原因，保留当前单据与日志，通过正式业务入口修正；不删除旧轮证据。":
              - textbox [disabled]: 记录失败原因，保留当前单据与日志，通过正式业务入口修正；不删除旧轮证据。
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
            - cell "发现失败时":
              - textbox [disabled]: 发现失败时
      - heading "割接后保障" [level=4]
      - table:
        - rowgroup:
          - row "序号 责任人 时间":
            - columnheader "序号":
              - generic: 序号
            - columnheader "责任人":
              - generic: 责任人
            - columnheader "时间":
              - generic: 时间
      - table:
        - rowgroup:
          - row "1 管理员（隔离验收） 本轮验收结束前核对状态与历史，不安排真实人员值守。":
            - cell "1":
              - generic: "1"
            - cell "管理员（隔离验收）":
              - textbox [disabled]: 管理员（隔离验收）
            - cell "本轮验收结束前核对状态与历史，不安排真实人员值守。":
              - textbox [disabled]: 本轮验收结束前核对状态与历史，不安排真实人员值守。
      - heading "审批记录" [level=3]:
        - text: 审批记录
      - generic: 已通过
      - button "刷新":
        - generic: 刷新
      - table:
        - rowgroup:
          - row "审批节点 状态 审批人／角色 五项评审及反馈 审批时间":
            - columnheader "审批节点":
              - generic: 审批节点
            - columnheader "状态":
              - generic: 状态
            - columnheader "审批人／角色":
              - generic: 审批人／角色
            - columnheader "五项评审及反馈":
              - generic: 五项评审及反馈
            - columnheader "审批时间":
              - generic: 审批时间
      - table:
        - rowgroup:
          - row "1. 发起人 已通过 管理员 仅批准当前第2轮本地隔离验收方案。已核对新单15项清单、两条新采集、六份非生产附件、六类步骤及四类手填样例保障；不授权生产割接或真实人员派工，S5及项目闭环另行核验。 准备工作：合理 业务测试：合理 割接操作：合理 回退步骤：合理 其他事项：合理 2026/10/16 01:59":
            - cell "1. 发起人":
              - generic: 1. 发起人
            - cell "已通过":
              - generic: 已通过
            - cell "管理员":
              - generic: 管理员
            - cell "仅批准当前第2轮本地隔离验收方案。已核对新单15项清单、两条新采集、六份非生产附件、六类步骤及四类手填样例保障；不授权生产割接或真实人员派工，S5及项目闭环另行核验。 准备工作：合理 业务测试：合理 割接操作：合理 回退步骤：合理 其他事项：合理":
              - text: 仅批准当前第2轮本地隔离验收方案。已核对新单15项清单、两条新采集、六份非生产附件、六类步骤及四类手填样例保障；不授权生产割接或真实人员派工，S5及项目闭环另行核验。
              - list:
                - listitem: 准备工作：合理
                - listitem: 业务测试：合理
                - listitem: 割接操作：合理
                - listitem: 回退步骤：合理
                - listitem: 其他事项：合理
            - cell "2026/10/16 01:59":
              - generic: 2026/10/16 01:59
          - row "2. 服务经理 已通过 管理员 服务经理隔离样例评审：五项方案与低风险C级测试范围一致。两次采集仅本机模拟器且已回传，四类保障均明确非真实人员；批准当前样例流程继续，不作生产割接批准或项目终验结论。 准备工作：合理 业务测试：合理 割接操作：合理 回退步骤：合理 其他事项：合理 2026/10/16 01:59":
            - cell "2. 服务经理":
              - generic: 2. 服务经理
            - cell "已通过":
              - generic: 已通过
            - cell "管理员":
              - generic: 管理员
            - cell "服务经理隔离样例评审：五项方案与低风险C级测试范围一致。两次采集仅本机模拟器且已回传，四类保障均明确非真实人员；批准当前样例流程继续，不作生产割接批准或项目终验结论。 准备工作：合理 业务测试：合理 割接操作：合理 回退步骤：合理 其他事项：合理":
              - text: 服务经理隔离样例评审：五项方案与低风险C级测试范围一致。两次采集仅本机模拟器且已回传，四类保障均明确非真实人员；批准当前样例流程继续，不作生产割接批准或项目终验结论。
              - list:
                - listitem: 准备工作：合理
                - listitem: 业务测试：合理
                - listitem: 割接操作：合理
                - listitem: 回退步骤：合理
                - listitem: 其他事项：合理
            - cell "2026/10/16 01:59":
              - generic: 2026/10/16 01:59
- button "刷新业务结果":
  - generic: 刷新业务结果
- button "刷新执行状态（不重载业务表单）":
  - generic: 刷新执行状态（不重载业务表单）
- region "任务状态操作"


```
