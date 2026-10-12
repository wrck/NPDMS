# 新迁入角色空审计人纠正：整批待裁决清单

状态：USER_APPROVED_AND_VERIFIED。用户明确批准“仅这13个角色25处按上述条件纠正”；仅本任务原成功运行中新建的13个角色、25处审计字段。没有新增表或列。

原同步任务：2108554583850524673；成功运行：2108559110330822657。

纠正预览任务：2108567804242235394；预览运行：2108567804707803137，PREVIEW_READY，13条UPDATED。预览业务写入0。

原fnd_roles的createBy/updateBy为NULL，对应system_role字段错误落为管理员字符串1。仅恢复清单字段为NULL；角色定义、权限、数据范围、创建/更新时间及原来源证据全部保持。角色表有默认更新日期行为，纠正SQL显式保护原update_time。

| 原角色ID | 角色编码 | 角色名称 | 正式角色ID | 待纠正字段 |
|---|---|---|---|---|
| 14 | callbackPer | 回访人员 | 2108559111635251202 | creator、updater：1 → NULL |
| 9 | areaLeader | 办事处主任 | 2108559111639445506 | creator、updater：1 → NULL |
| 10 | engineeManagerLeader | 工程管理部主管 | 2108559111643639809 | creator、updater：1 → NULL |
| 13 | engineeManager | 工程管理部 | 2108559111652028417 | creator、updater：1 → NULL |
| 16 | financialStaff | 财务人员 | 2108559111656222721 | creator、updater：1 → NULL |
| 17 | presalesStaff | 售前测试专员 | 2108559111660417025 | creator、updater：1 → NULL |
| 18 | probAdmin | 技术公告管理员 | 2108559111668805634 | creator、updater：1 → NULL |
| 19 | probSupporter | 技术支持人员 | 2108559111672999938 | creator、updater：1 → NULL |
| 20 | probRD | 研发人员 | 2108559111677194241 | creator、updater：1 → NULL |
| 21 | warrantyCallbacker | 项目维保回访专员 | 2108559111685582849 | creator、updater：1 → NULL |
| 6 | projectViewer | 项目查阅员 | 2108559111689777154 | creator、updater：1 → NULL |
| 22 | componentAdmin | 产品组件管理员 | 2108559111698165761 | creator、updater：1 → NULL |
| 1 | admin | 管理员 | 2108559111702360065 | updater：1 → NULL |

执行前提：来源11列、原CREATED回执、原绑定及正式角色完整字段仍与预览一致。任意人工修改或日期/定义/范围变化均阻断；事务内锁定并核对全部目标，回执失败时业务纠正一起回滚。重复纠正返回UNCHANGED。既有角色及菜单、账号角色、公司部门范围关系不修改。

验证：SYSTEM事务9项及INT适配器4项通过，涵盖实际自动填充、历史日期保护、人工变化阻断、来源/回执身份及失败回滚。纠正运行2108568396289216514为SUCCESS，13条UPDATED；实际落库核对25个新角色、31条原来源绑定，原15个角色及角色菜单/用户角色/范围关系全部不变，25处NULL及全部历史日期均正确。重复预览2108568455621840898为PREVIEW_READY、13条UNCHANGED，完整前后值一致。此前获准的557处仅适用于用户更新人；本次依据独立限定批准执行。
