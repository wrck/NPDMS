# 来源基础数据复用系统字典的整表裁决及执行

状态：TYPE_TABLE_MIGRATED_AND_VERIFIED / DATA_TABLE_LOCAL_VERIFIED。类型表80条、字典项531条均已通过迁移功能及SYSTEM Owner承接、重放及真实页面验证；分别保持11列和14列。原空ID114按独立裁决显式跳过，3个字典项属性及3个类型属性按裁决逐列不迁入。本文件不以来源证据代替业务承接。

字典项最初裁决及提案追溯：用户2026-10-12选择“保留既有结构，我指定缺项处理”。system_dict_data保持14列，attribute1/effective_from/effective_to三个物理新增候选停止实施。当时属性排除、排序/日期及ID114处理等待补答；现已由下段独立裁决解决，不再作为当前阻断项。

后续明确裁决：“三属性不迁入，其余按建议”。原basicDataAttri1、effectiveFrom、effectiveTo逐列不迁入，原因均为“既有结构无同义字段，按用户裁决不承接”；不挪用备注、CSS、创建日期。NULL排序取0，无创建时间采用实际迁入时间，NULL更新时间取创建时间，原NULL人员保持；原ID114因无业务键/标签/属性不迁入。最新原表532行×12列及正式1575行×14列重新读取，无原业务键重复或精确现存冲突。排除ID114后计划531条，其中32条排序初始化0、128条创建日期使用实际迁入时间；原总33/129均含被排除空记录。7条原有效期已结束，按整表原启停建议初始化正式停用，不保存被裁决排除的区间列或激活旧URL。仅原ID542创建人system缺账号，用户进一步明确批准这一处原编码保存正式creator，不造账号/权限，其余人员精确关联既有SYS账号。

三个原缺属性的不可变来源证据仍保留，但不作为业务承接。SYSTEM迁移Owner复用全部14正式列及类型启用、业务键唯一校验，INT全读12原字段并通过公开API调用；无新DDL，不修改普通字典维护。现存字典项不自动接管，成功绑定后的任意正式字段、原字段或历史审计变化均阻断；初始化审计只在首次运行产生，重放保持原首次值。

类型表最初裁决及提案追溯：system_dict_type 用户选择“保留既有结构，我指定缺项处理”。该表保持11列，management_visible、effective_from、effective_to三个新增候选停止实施；下文候选保留为提案追溯，不代表已获批准。当时原属性排除及49个缺定义分组待补答，现均已由下一段独立裁决解决。

2026-10-12补充裁决：用户明确“1可以不要这几个字段”，批准原status（前台管理标记）、effectiveFrom、effectiveTo不迁入。逐列排除原因：现system_dict_type没有同义字段，用户明确保持既有11列并批准不迁入；不挪用正式启停、备注或审计时间。随后用户批准“按上述规则补齐类型定义”，49个缺定义分组以原分组编码为type和暂用name，审计采用实际迁入人员/时间；31个已有定义保留原名称和历史时间，NULL更新时间用原创建时间，NULL人员保持。类型表已执行并验证，下方三个候选只作已撤回提案追溯；字典项按自身独立裁决执行，不引用类型表审批替代。

依据2026-10-11完整原表材料及本次正式Owner代码核查：fnd_basic_data_type 10列31条；fnd_basic_data 12列532条、80个分组。49个分组没有原类型定义。该材料中的301个正式类型，与80个原编码及31个原中文类型名均没有精确重复；实际实施前仍须重新预览，不把此旧材料当作当前落库授权。

建议复用system_dict_type、system_dict_data，不建“来源字典”并行表。原分组编码直接作为业务type/dict_type，不拿原id拼编码；实际原键值直接作为value。已有同义业务字典如需合并，必须先逐码核实含义与值域，再另裁决，不能因中文近似混入当前状态机、角色或权限。

## system_dict_type：既有11列与3个候选新增列

| 正式列 | 建议承接及处理 |
|---|---|
| id | 由SYSTEM生成；原id进入已有迁移绑定 |
| name | 原dataTypeName完整保存；31个原定义都有真实名称 |
| type | 原dataTypeCode原样业务编码 |
| status | 保留现平台0正常/1停用含义；原类型全部有效期未结束，新增原定义建议初始化0，不接管既有类型 |
| remark | 不挪用承接原管理标记或有效期 |
| creator | 原createBy按已批准SYS用户绑定精确关联；原NULL保持NULL |
| create_time | 原31条createTime均非NULL，保留原时间 |
| updater | 原updateBy全部NULL，保持NULL |
| update_time | 已批准：原updateTime全部NULL，用各自原createTime初始化，原NULL在来源证据列明 |
| deleted | 新记录false，不删除、恢复或覆盖既有记录 |
| deleted_time | 原无删除日期，NULL |
| 原status：不新增management_visible | 已裁决不迁入；现结构无同义字段，用户明确保持11列并批准排除，不挪用启停或备注 |
| 原effectiveFrom：不新增effective_from | 已裁决不迁入；同上，不挪用创建时间 |
| 原effectiveTo：不新增effective_to | 已裁决不迁入；同上，不挪用删除时间或启停 |

原全部10字段：id→绑定，dataTypeCode→type，dataTypeName→name，createTime/createBy/updateTime/updateBy→正式审计；status、effectiveFrom、effectiveTo已获明确不迁入裁决，逐列原因如上。既有11列及名称、审计、缺失定义补齐规则已批准，无新增物理列。

49个没有类型定义的分组：已批准原dataTypeCode作为正式type，name亦使用原编码，不猜造中文业务名称；创建/更新时间及人员用实际迁入运行的时间和操作者，不伪造原类型记录。首次承接后由正式绑定保留首次审计，重放不随运行更换。不得因类型表缺行丢弃分组字典项；字典项本身仍等待独立整表裁决。

## system_dict_data：既有14列与3个已停止新增的候选

| 正式列 | 建议承接及处理 |
|---|---|
| id | SYSTEM生成，原id通过来源绑定 |
| sort | 原sortId按旧实际升序直接使用；已批准NULL初始化0，实际承接32条，另1条属于排除ID114 |
| label | 原basicDataName；不猜造NULL名称 |
| value | 原basicDataId；原业务键原样，禁止用id替代 |
| dict_type | 原dataTypeCode，精确关联经裁决的类型 |
| status | 正式启停含义；新增项建议按原有效期初始化，不把原类型管理标记用于启停；既有项不覆盖 |
| color_type | 原无同义值，NULL |
| css_class | 原无同义值，NULL，不塞属性1 |
| remark | 原无同义值，NULL，不塞属性1或原有效期 |
| creator | 原createBy精确关联SYS；NULL保持；仅ID542的system经独立裁决保留原编码 |
| create_time | 403条原时间保留；已批准原NULL用实际迁入时间，实际承接128条，另1条属排除ID114；原NULL列明 |
| updater | 原全部NULL，保持NULL |
| update_time | 已批准原NULL取创建时间；首次初始化后重放保持首次值 |
| deleted | 新记录false；不删除或恢复现存数据 |
| 原basicDataAttri1：不新增attribute1 | 用户明确不迁入；既有结构无同义字段，按用户裁决不承接，不挪用备注/CSS或执行旧入口 |
| 原effectiveFrom：不新增effective_from | 用户明确不迁入；既有结构无同义字段，按用户裁决不承接，不当创建时间；首次启停按原区间转换 |
| 原effectiveTo：不新增effective_to | 用户明确不迁入；既有结构无同义字段，按用户裁决不承接；7条结束项初始化停用 |

原全部12字段：id→绑定，dataTypeCode→dict_type，basicDataId→value，basicDataName→label，sortId→sort，四审计字段→正式审计；basicDataAttri1/effectiveFrom/effectiveTo已获明确不迁入裁决，逐列原因如上。三个物理新增候选均停止，排序与审计初始化已批准并实际核验。

原ID114是唯一value/label为NULL的记录，其属性、排序、全部审计、有效期均NULL，仅保留id与分组23。用户明确批准“不迁入字典项：没有业务键、标签或属性，不构成可用字典项”，真实运行返回SKIPPED并保存理由，不创建目标或绑定。排除前核实全部原字段；有后续业务值则阻断，不沿旧裁决丢弃新值。

## Owner与保护

两张字典表均为既有全局表，不增加tenant_id、不擅自改成租户模型。迁入通过迁移/同步和SYSTEM Owner，保持正式类型/名称、类型＋键值唯一校验；不能绕过当前保存逻辑插入伪造键值。历史原NULL用显式Owner写入保护，避免框架填入管理员。对已有当前状态机、角色、菜单、前台入口不产生授权或流程激活。

system_dict_type整表及49个缺失定义、system_dict_data剩余属性排除/排序及审计初始化/ID114排除均已通过独立裁决，无新增列。与现有业务字典的同义合并未获批准，已有定义或字典项不接管。

## 类型表实施及实际验证边界

SYSTEM正式迁移Owner、全11列显式插入Mapper及INT类型模板/适配器已实现，不改普通字典维护功能。原31定义保留精确原审计；49个缺定义分组以实际迁入运行时间和人员初始化，重放用首次正式审计，不随新运行改写。全局表不加tenant_id，调用仍核对租户、运行/配置版本、精确账号及普通字典创建权限；现存同名同码冲突和绑定后续改变阻断，不接管既有定义。

4项Owner、5项适配器、3项实际MySQL临时表检查通过。首次适配器用例误将平台毫秒日期编码按ISO文本断言，已改为真实类型解码及重放审计核验并通过，未降低业务校验。MySQL已准确保存原NULL人员及历史日期、核验数据库字符比较规则下名称/编码冲突和失败回滚，临时表测试未写正式表。

完整打包初次在C盘明确报磁盘空间不足，保留失败日志；改用D盘新建构建副本后41模块完整打包通过，原源码、副本和其他改动不删除。Docker管理接口随后无响应，切换被前置检查阻断，旧后端没有停止；按既有基础设施恢复授权限定恢复Docker Desktop/docker-desktop，原24306/24379容器及卷、端口保持并恢复健康。完成构件切换后首次来源预览因启动脚本遗漏已有数据源解密配置失败（2109351155056177153、读取0条、无业务写入）；补齐指定本地配置的内存读取后按既有重启授权恢复，未更换密钥、修改密文或来源连接，失败回执保留。

正式任务2109351154645135362：重新预览2109351862001872897为PREVIEW_READY/CREATED80，原301条及受保护业务表完整值保持；执行2109351965311774721为SUCCESS/CREATED80。31条原定义完整业务字段和历史审计一致，原NULL更新人保持；49条缺失定义采用原编码及本次运行的实际人员/时间。正式表共381条（含1条原逻辑删除记录），仍为11列，未执行DDL。来源绑定80条与成功回执、正式11字段逐项一致。原status及两个有效期仅按用户排除裁决留在不可变来源证据，不作为业务承接。

实际重放2109352251665297409为SUCCESS/UNCHANGED80：完整381条及11列结构与执行后证据一致，49条初始化审计未随新运行变化，其他受保护业务表保持。核对脚本按平台JSON编码处理Long字符串和毫秒日期，不把序列化差异当作数据差异。任务私有dictionary-type-approved-applied-proof-20261012.json、dictionary-type-approved-replay-proof-20261012.json保留全字段与绑定证据。

真实浏览器从系统管理→字典管理查询：原01显示“快递公司”及2015-05-22 17:21:01；缺定义trackStatus显示同名编码及2026-10-12 02:34:33。页面原编码前导零保持，均为1条结果；没有保存、删除或激活旧入口。DOM证据分别为任务私有dictionary-type-original-browser-20261012.txt、dictionary-type-missing-definition-browser-20261012.txt。当时仅类型完成；字典项后来按独立裁决完成，见下一节。

## 字典项实际执行及验证

正式任务2109371486114320385，版本0。预览2109371486424698881为PREVIEW_READY，读取532、CREATED531/SKIPPED1；执行2109371785830895618为SUCCESS，2026-10-12 03:53:18至03:53:22，CREATED531/SKIPPED1；普通重放2109371924867878913为SUCCESS，UNCHANGED531/SKIPPED1，无现存数据接管。均通过迁移/同步和SYSTEM公开Owner，未直接脚本写业务表。

正式字典项共2106条。原1575条全部14列保持；531条新增与原12字段、转换结果及绑定逐项核实；正式381条类型全部11列及字典项14列结构保持，原532条全部12列保持。403条原创建时间保留，128条原NULL以首次执行03:53:18初始化，32条NULL排序取0；NULL人员未被框架填为管理员。仅原ID542的system保存正式creator；7条结束项正式status=1。531条绑定与成功回执一致，ID114不创建目标或绑定。重放全2106条14列一致，首次初始化审计没有随新运行变化。

5项SYSTEM Owner、5项INT适配器、4项真实MySQL临时表验证通过，涵盖权限、精确类型、NULL/历史审计、字符比较规则、后续改变阻断和失败回滚；展示9项验证及41模块完整打包通过。初次测试替身重设触发NULL查询的测试错误已修正，失败日志保留，业务规则未降低。经已授权切换部署59282；19082及其他应用服务保持。没有字典DDL或Flyway修复。

真实浏览器已核验正式字典页的顺丰value=0、type=01、排序1、历史2015-05-22 17:21:29，及项目任务节点“业务割接”value=50、排序5、关闭状态。迁移详情显示读取532、新增531/跳过1；字段对照明确分开正式14列与原表12列，原NULL更新日期、人员、前导零及被排除属性仍可作为来源证据复查，未挪用正式备注/CSS。重放页面显示跳过1、未变化531。DOM保留于任务私有dictionary-data-apply-browser-20261012.txt、dictionary-data-apply-fields-browser-20261012.txt、dictionary-data-replay-browser-20261012.txt及native字典页证据。

字段对照另已核验原ID542：正式creator和原createBy均为system，原2026-05-27 19:39:09、NULL更新人及14字段重放全部一致；原ID114显示跳过及明确排除理由。首次验收发现处理明细名称列未识别正式item.label，已仅对本对象复用正式标签，真实热更新后显示“License授权”，不从来源证据猜名称、不改变回执。证据为dictionary-data-system-creator-browser-20261012.txt、dictionary-data-skipped-browser-20261012.txt、dictionary-data-replay-names-browser-20261012.txt；此局部文字展示修正以真实浏览器验证，没有重复后端打包或业务写入。

完整逐字段核对材料为任务私有dictionary-data-original-audit-20261012.json、dictionary-data-preview-proof-20261012.json、dictionary-data-applied-proof-20261012.json、dictionary-data-replay-proof-20261012.json。同期成员表145→147，原145条未改；新增两条属于项目992203060200的独立普通成员操作，已找到PROJECT_MEMBER_ADD及PROJECT_MANAGERS_UPDATE真实审计，不纳入本次迁移纠正或撤回。不能声明同期成员全表没有新增。

本项完成指定任务环境的业务写入、重放及真实页面核验；生产Flyway基线已有校验差异仍单独待处理，不因本项无DDL宣称全历史或生产上线完成。
