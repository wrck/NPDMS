# system_users 已裁决账号范围

仅迁移首字母＋工号、*_ss、*_supp；不修剪、改名，不从格式直接猜工号真实性。启停仍沿已裁决状态转换。

原表读取均未含密码；没有结构、账号或权限写入。

| 项目 | 数量 |
|---|---:|
| t_user符合范围原记录 | 329 |
| fnd_user_info符合范围原记录 | 866 |
| 合并后不同账号名 | 1176 |
| 对应t_user_info任职记录 | 330 |
| 仍不满足当前登录输入规则的选中账号 | 304 |

## 被排除的历史审计引用

| 来源操作者 | 选中账号的来源审计引用数 | 裁决状态 |
|---|---:|---|
| admin | 99 | 待指定正式用户；不能据同名关联 |

## 按用户范围不迁入的账号

admin、sse、test等先前同名合并提案已失效。*_supp1/2/3不等于用户写出的*_supp，末尾U+00A0不等于*_ss；当前严格按原字符串匹配。若用户另给范围规则再重新核对。

| 原表 | 来源主键 | 原账号（不可见字符明示） | 不迁入原因 |
|---|---|---|---|
| t_user | 0 | admin | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 999999 | test | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000000 | sse | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000046 | luoxinyu | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000053 | zhengjienan | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000057 | mengjunliang | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000067 | xialangguo_ss[U+00A0] | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000079 | yangxingwen | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000081 | liuqi | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000140 | ap | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000205 | jiangyating | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| t_user | 1000206 | lanxuhui | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 81 | admin | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 228 | 梁红玉 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 273 | testpublic | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 309 | 赵炯粟 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 390 | 杨少毅 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 473 | pms_tbpublic | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 490 | 胡慧东 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 508 | test_supp2 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 509 | test_supp1 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 535 | test_supp3 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 550 | sys | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 785 | sse | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 847 | 白凯 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |
| fnd_user_info | 870 | DS31024 | 用户限定仅迁移首字母+工号、*_ss、*_supp；原账号原样不满足本轮规则，不修剪或改名。 |

## 已选中但输入规则未覆盖的账号

- anxingcheng_ss
- baoweiliang_ss
- baseline_supp
- bjysy_supp
- caiyunfei_ss
- caofengxun_supp
- caoxuesong_ss
- chenchen_supp
- chendinghong_supp
- chenggang_supp
- chenghaoyu_ss
- chengliming_ss
- chengpenghong_ss
- chengwantao_ss
- chenjiayan_ss
- chenjingyu_ss
- chennaiqing_supp
- chenpeng_supp
- chenqianbing_supp
- chensijie_ss
- chenwanbin_ss
- chenwanxin_ss
- chenyichao_ss
- chenyiyuan_supp
- chenyongxiang_supp
- chenyu_ss
- chenyuhang_supp
- chenzhe_supp
- cuichengshu_ss
- cuihaofan_supp
- cuihaolei_supp
- dbyh_supp
- dcl_supp
- dinghe_supp
- dingwei_supp
- dingxu_supp
- dongchangyin_supp
- dongfulin_supp
- fanchenglv_ss
- fangsensen_supp
- fengchengkuan_ss
- fenghaopeng_ss
- fhtree_supp
- fuhongchen_ss
- fuyu_ss
- gangyunfei_supp
- ganjialu_supp
- gaoxiang_ss
- gaoxinglong_supp
- gengzhao_supp
- gesiming_supp
- gongruixiang_ss
- guobingbing_supp
- guohuiqing_supp
- guolirong_supp
- guoshaoming_ss
- guqiuwu_supp
- hanminghong_supp
- hanwenhai_supp
- haoyu_ss
- hechenglei_supp
- houwei_supp
- huangbingliang_ss
- huangjiaxing_ss
- huangjunjie_supp
- huangqiang_supp
- huangxiaohui_ss
- huangxin_ss
- huangyanhui_supp
- huangzhen_supp
- huhao_supp
- huoliangwei_supp
- huoruibing_supp
- husiting_ss
- hutengfei_supp
- huyue_supp
- huzhenhui_supp
- jiabaotong_supp
- jianghan_supp
- jianghuaiguo_supp
- jiangxuemei_ss
- jiasihao_supp
- jikun_ss
- jingsuliang_supp
- konglinghui_supp
- lanxian_supp
- leichengbo_ss
- leizhanpeng_supp
- liangguangsong_ss
- lianghaopeng_supp
- liangrihao_supp
- liaozhou_ss
- lichangkang_supp
- lichen_supp
- lichenchen_ss
- lifan_supp
- lifanb_supp
- lifeilong_supp
- liguoqiang_ss
- limengnan_supp
- linchunhai_supp
- linhaoyuan_ss
- linkeyu_supp
- linqiang_supp
- linxijie_ss
- linzhipeng_supp
- litao_supp
- liubingtong_ss
- liudawei_ss
- liudebang_supp
- liuhaibo_ss
- liuhu_supp
- liujiazhen_supp
- liujie_ss
- liujun_ss
- liujunzhuo_supp
- liuliangliang_ss
- liuliping_ss
- liuqi_ss
- liuqian_ss
- liuweiwei_supp
- liuxing_supp
- liuxue_supp
- liuying_ss
- liuyingzheng_supp
- liuyong_ss
- liuzhuming_ss
- lixiao_ss
- liyadian_ss
- liyafan_ss
- liyaxian_supp
- liyongping_ss
- lizhao_ss
- lizhengpeng_supp
- lizhipeng_supp
- lnss_supp
- longxiaozhu_ss
- luojing_ss
- luoquanzhe_supp
- luosidang_ss
- luotengfei_ss
- luoyueyun_ss
- luozhi_supp
- lupengpeng_supp
- lvjiacheng_supp
- machao_ss
- maibenjian_ss
- maijiahui_ss
- mawenzhe_supp
- mengyutao_supp
- miaochangyang_supp
- miaotian_ss
- niushuren_supp
- njck_supp
- njsc_supp
- njyn_supp
- ouyangzhaofeng_supp
- panchaozhong_ss
- pengjunjie_ss
- puyuhang_ss
- qiaojingbo_ss
- qiubaohui_ss
- qiurenwu_ss
- qiuzhenlu_supp
- raojie_ss
- renchangchao_supp
- ruanhao_supp
- sczrxx_supp
- sdrk_supp
- shaoxinchao_supp
- songjian_ss
- songrui_ss
- suibensen_ss
- sunchaoran_supp
- sunchengwu_supp
- sunhaoran_ss
- sunruimin_supp
- sunzhikui_ss
- suzhengxiong_supp
- szbb_supp
- taiqihang_supp
- tangchengyang_ss
- tangjiaming_ss
- tangle_ss
- tangxueke_ss
- tanjiawei_ss
- tanpengcheng_ss
- tanyang_supp
- tengfarong_supp
- test_supp
- tianguo_ss
- tianjunhui_supp
- tianlifu_supp
- tycw_supp
- wangchengyu_supp
- wangchenxu_supp
- wangchenyang_supp
- wanghao_supp
- wangkang_supp
- wanglei_supp
- wangqinzheng_ss
- wangruixiang_ss
- wangxiaoyi_ss
- wangxinglu_ss
- wangxinwei_supp
- wangxiutao_supp
- wangxuan_supp
- wangxuesong_ss
- wangxuhui_ss
- wangye_supp
- wangyib_ss
- wangzhiheng_ss
- wangzhipeng_ss
- weichunting_ss
- wengencong_supp
- wudexin_ss
- wuhao_ss
- wujiang_supp
- wujingwen_ss
- wuzhiwen_ss
- wuzhongqi_supp
- xiaheting_ss
- xialangguo_ss
- xiaokun_supp
- xiaoqinwen_ss
- xiaoyuanyuan_ss
- xiatongsen_supp
- xieqianzhu_ss
- xingfubao_ss
- xiongqiang_ss
- xizhe_supp
- xufan_ss
- xuqiang_supp
- xushang_supp
- xuxiangqian_ss
- xuzhenkui_supp
- yangchen_ss
- yangdanni_ss
- yangde_supp
- yangkai_supp
- yangkaitian_supp
- yangqingsong_ss
- yangshubo_supp
- yangsujin_supp
- yangxing_supp
- yangxingwen_ss
- yangyun_supp
- yangzeng_supp
- yanxinrui_supp
- yanzuo_supp
- yejinliang_ss
- yewenshuo_supp
- yezhihao_ss
- yinhailong_supp
- ysy_supp
- yuqinqin_ss
- zcrh_supp
- zdkj_supp
- zengjian_supp
- zengzhipeng_ss
- zhangdongsheng_ss
- zhangguangyu_ss
- zhanghongfei_ss
- zhanghuidong_ss
- zhangjin_ss
- zhangjufeng_supp
- zhanglele_supp
- zhangnan_supp
- zhangxizhen_ss
- zhangyongbing_supp
- zhangyulong_supp
- zhangzhengxing_supp
- zhaofangfang_ss
- zhaohongyu_supp
- zhaoyangxi_supp
- zhaozidu_supp
- zhenghao_ss
- zhengjiaqi_ss
- zhengjienan_ss
- zhengjieyuan_supp
- zhengkang_supp
- zhengtao_supp
- zhengzhiwen_ss
- zhonghaotian_supp
- zhouchao_supp
- zhouduqun_ss
- zhouguangmin_supp
- zhoujun_ss
- zhouqitao_supp
- zhoutangfu_supp
- zhouxinwei_supp
- zhouzisong_supp
- zhujiangfeng_supp
- zhujie_supp
- zhuxiaolong_ss
- zhuyunrong_ss
- zhuyuwei_ss
- zhuzexuan_ss
- zhuziyang_ss
- zoujin_supp
- zoukaiping_ss
- zoupeng_ss
- zousonghe_ss
- zycx_supp

## 最后五项用户字段与关联裁决

- 保留选中*_ss、*_supp原登录名；登录与账号维护输入兼容这些形式。注册规则不在此裁决范围。
- 旧admin不迁入账号资料，仅将历史创建/更新人关联当前admin ID1；不导入旧角色或授权；目标缺失或绑定改变时阻断。
- 用户明确例外：原值@dptech.com直接承接正式email，不置空、不改写、不用custom_info替代。普通新建/修改邮箱格式校验保持。
- 正式email仅去除来源尾部制表符，原值完整留来源证据。
- z04214、w04218、y04223、w04261、z04264迁入正式用户角色关系，原公司NULL留证据；不新增公司或办事处授权。

以上覆盖此前同项待裁决建议；来源原始记录不改写。代码接入及正式迁移结果另行核验，不代表已经落库。
