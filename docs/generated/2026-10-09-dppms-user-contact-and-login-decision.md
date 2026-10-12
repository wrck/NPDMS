# system_users 联系人与登录账号补充裁决

用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。

用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。

此前重复联系人改存JSON的提案撤回。以下27组邮箱、11组手机号及317个登录名是原全来源审查证据，不是当前选中账号数量；保留原清单供追溯，实际迁移按[最新范围清单](2026-10-09-dppms-user-selected-scope-decision.md)。登录输入兼容仍待裁决。没有实际账号或权限写入。

| 类别 | 组号 | 来源账号 |
|---|---:|---|
| email | 1 | admin、j01441 |
| email | 2 | ap、pms_tbpublic、sse、sys、test、test_supp、test_supp1、test_supp2、test_supp3、w02611 |
| email | 3 | b05761、白凯 |
| email | 4 | bjysy_supp、ouyangzhaofeng_supp |
| email | 5 | c03683、c3683 |
| email | 6 | d00537、testpublic |
| email | 7 | d02112、z02112 |
| email | 8 | g04303、g04304 |
| email | 9 | guohuiqing_supp、tycw_supp |
| email | 10 | h01564、胡慧东 |
| email | 11 | h06486、y06486 |
| email | 12 | i05079、l05079、p05079 |
| email | 13 | j06309、jiangyating |
| email | 14 | l00450、梁红玉 |
| email | 15 | lifan_supp、lifanb_supp |
| email | 16 | liuqi、liuqi_ss |
| email | 17 | m04489、mengjunliang |
| email | 18 | p01537、w04649 |
| email | 19 | sdrk_supp、yinhailong_supp |
| email | 20 | t03048、y03048 |
| email | 21 | w02098、w02787 |
| email | 22 | xialangguo_ss、xialangguo_ss[U+00A0] |
| email | 23 | y04046、杨少毅 |
| email | 24 | y05387、y05431 |
| email | 25 | yangxingwen、yangxingwen_ss |
| email | 26 | z03746、赵炯粟 |
| email | 27 | zhengjienan、zhengjienan_ss |
| mobile | 1 | g05890、gaoxiang_ss |
| mobile | 2 | g06053、zhuzexuan_ss |
| mobile | 3 | j06309、jiangyating |
| mobile | 4 | l05586、liuying_ss |
| mobile | 5 | liuqi、liuqi_ss |
| mobile | 6 | m04489、mengjunliang |
| mobile | 7 | xialangguo_ss、xialangguo_ss[U+00A0] |
| mobile | 8 | yangxingwen、yangxingwen_ss |
| mobile | 9 | z04838、zhenghao_ss |
| mobile | 10 | z06174、zhangguangyu_ss |
| mobile | 11 | zhengjienan、zhengjienan_ss |

## 登录及账号维护输入规则

现有AuthLoginReqVO/UserSaveReqVO仅允许4~30位英文字母及数字；当前来源存在中文、下划线、短账号及不可见尾字符。未经裁决不改写旧账号，也不放宽当前认证接口。

以下 317 个不同原账号不能通过当前输入规则。是否保留原登录名并明确批准兼容旧账号的输入规则，或指定新账号编码，由用户裁决。不可见尾字符不能自动修剪或合并。

| 原账号（不可见字符明示） | 来源身份 | 来源姓名 | 原状态 |
|---|---|---|---|
| anxingcheng_ss | t_user:1000126 |  | 1 |
| ap | t_user:1000140 |  | 1 |
| baoweiliang_ss | t_user:1000192 |  | 1 |
| baseline_supp | fnd_user_info:518 | 北京贝莱伟业 | 1 |
| bjysy_supp | fnd_user_info:565 | 北京亿数云 | 1 |
| caiyunfei_ss | t_user:1000059 |  | 0 |
| caofengxun_supp | fnd_user_info:717 | 曹封勋 | 1 |
| caoxuesong_ss | t_user:1000022 |  | 1 |
| chenchen_supp | fnd_user_info:856 | 陈晨 | 1 |
| chendinghong_supp | fnd_user_info:680 | 陈定红 | 0 |
| chenggang_supp | fnd_user_info:719 | 程刚 | 1 |
| chenghaoyu_ss | t_user:1000114 |  | 1 |
| chengliming_ss | t_user:1000214 |  | 1 |
| chengpenghong_ss | t_user:1000146 |  | 1 |
| chengwantao_ss | t_user:1000213 |  | 1 |
| chenjiayan_ss | t_user:1000203 |  | 1 |
| chenjingyu_ss | t_user:1000188 |  | 0 |
| chennaiqing_supp | fnd_user_info:598 | 陈乃青 | 0 |
| chenpeng_supp | fnd_user_info:599 | 陈鹏 | 1 |
| chenqianbing_supp | fnd_user_info:833 | 陈乾炳 | 1 |
| chensijie_ss | t_user:1000016 |  | 0 |
| chenwanbin_ss | t_user:1000137 |  | 1 |
| chenwanxin_ss | t_user:1000196 |  | 1 |
| chenyichao_ss | t_user:1000124 |  | 1 |
| chenyiyuan_supp | fnd_user_info:720 | 陈依媛 | 1 |
| chenyongxiang_supp | fnd_user_info:696 | 陈永祥 | 1 |
| chenyu_ss | t_user:1000156 |  | 1 |
| chenyuhang_supp | fnd_user_info:868 | 陈预航 | 1 |
| chenzhe_supp | fnd_user_info:689 | 陈哲 | 0 |
| cuichengshu_ss | t_user:1000193 |  | 1 |
| cuihaofan_supp | fnd_user_info:737 | 崔浩帆 | 1 |
| cuihaolei_supp | fnd_user_info:866 | 崔皓雷 | 1 |
| dbyh_supp | fnd_user_info:516 | 洛阳鼎邦源华 | 0 |
| dcl_supp | fnd_user_info:510 | 杭州德昌隆 | 0 |
| dinghe_supp | fnd_user_info:707 | 丁赫 | 1 |
| dingwei_supp | fnd_user_info:803 | 丁伟 | 1 |
| dingxu_supp | fnd_user_info:743 | 丁旭 | 1 |
| dongchangyin_supp | fnd_user_info:536 | 董常胤 | 0 |
| dongfulin_supp | fnd_user_info:861 | 董福临 | 1 |
| fanchenglv_ss | t_user:1000096 |  | 1 |
| fangsensen_supp | fnd_user_info:855 | 方森森 | 1 |
| fengchengkuan_ss | t_user:1000061 |  | 1 |
| fenghaopeng_ss | t_user:1000174 |  | 1 |
| fhtree_supp | fnd_user_info:523 | 徐州绯红树 | 0 |
| fuhongchen_ss | t_user:1000049 |  | 0 |
| fuyu_ss | t_user:1000168 |  | 1 |
| gangyunfei_supp | fnd_user_info:748 | 刚云飞 | 0 |
| ganjialu_supp | fnd_user_info:529 | 甘佳露 | 0 |
| gaoxiang_ss | t_user:1000181 |  | 1 |
| gaoxinglong_supp | fnd_user_info:801 | 高兴隆 | 1 |
| gengzhao_supp | fnd_user_info:709 | 耿昭 | 1 |
| gesiming_supp | fnd_user_info:712 | 葛思明 | 0 |
| gongruixiang_ss | t_user:1000191 |  | 1 |
| guobingbing_supp | fnd_user_info:853 | 郭兵兵 | 1 |
| guohuiqing_supp | fnd_user_info:362 | 郭辉青 | 1 |
| guolirong_supp | fnd_user_info:797 | 郭力荣 | 1 |
| guoshaoming_ss | t_user:1000207 |  | 1 |
| guqiuwu_supp | fnd_user_info:670 | 顾秋武 | 1 |
| hanminghong_supp | fnd_user_info:708 | 韩明宏 | 1 |
| hanwenhai_supp | fnd_user_info:693 | 韩文海 | 1 |
| haoyu_ss | t_user:1000063 |  | 1 |
| hechenglei_supp | fnd_user_info:755 | 何成磊 | 1 |
| houwei_supp | fnd_user_info:882 | 侯伟 | 1 |
| huangbingliang_ss | t_user:1000077 |  | 1 |
| huangjiaxing_ss | t_user:1000173 |  | 1 |
| huangjunjie_supp | fnd_user_info:820 | 黄俊杰 | 1 |
| huangqiang_supp | fnd_user_info:540 | 黄强 | 0 |
| huangxiaohui_ss | t_user:1000094 |  | 1 |
| huangxin_ss | t_user:1000086 |  | 1 |
| huangyanhui_supp | fnd_user_info:716 | 黄衍辉 | 0 |
| huangzhen_supp | fnd_user_info:710 | 黄振 | 1 |
| huhao_supp | fnd_user_info:733 | 胡皓 | 1 |
| huoliangwei_supp | fnd_user_info:863 | 霍良伟 | 1 |
| huoruibing_supp | fnd_user_info:783 | 霍瑞兵 | 1 |
| husiting_ss | t_user:1000058 |  | 1 |
| hutengfei_supp | fnd_user_info:543 | 胡腾飞 | 0 |
| huyue_supp | fnd_user_info:703 | 扈悦 | 1 |
| huzhenhui_supp | fnd_user_info:845 | 胡振辉 | 0 |
| jiabaotong_supp | fnd_user_info:714 | 贾宝通 | 1 |
| jianghan_supp | fnd_user_info:690 | 姜涵 | 1 |
| jianghuaiguo_supp | fnd_user_info:615 | 江怀国 | 0 |
| jiangxuemei_ss | t_user:1000021 |  | 1 |
| jiasihao_supp | fnd_user_info:728 | 贾巳灏 | 1 |
| jikun_ss | t_user:1000020 |  | 0 |
| jingsuliang_supp | fnd_user_info:652 | 景苏梁 | 1 |
| konglinghui_supp | fnd_user_info:883 | 孔令辉 | 1 |
| lanxian_supp | fnd_user_info:328 | 兰贤 | 0 |
| leichengbo_ss | t_user:1000116 |  | 1 |
| leizhanpeng_supp | fnd_user_info:715 | 雷展鹏 | 1 |
| liangguangsong_ss | t_user:1000002 |  | 0 |
| lianghaopeng_supp | fnd_user_info:813 | 梁豪鹏 | 1 |
| liangrihao_supp | fnd_user_info:808 | 梁日浩 | 1 |
| liaozhou_ss | t_user:1000186 |  | 1 |
| lichangkang_supp | fnd_user_info:812 | 李常康 | 1 |
| lichen_supp | fnd_user_info:691 | 李晨 | 0 |
| lichenchen_ss | t_user:1000197 |  | 1 |
| lifan_supp | fnd_user_info:617 | 李帆 | 0 |
| lifanb_supp | fnd_user_info:673 | 李帆 | 0 |
| lifeilong_supp | fnd_user_info:810 | 李飞龙 | 1 |
| liguoqiang_ss | t_user:1000149 |  | 1 |
| limengnan_supp | fnd_user_info:685 | 李梦男 | 0 |
| linchunhai_supp | fnd_user_info:701 | 林春海 | 1 |
| linhaoyuan_ss | t_user:1000025 |  | 1 |
| linkeyu_supp | fnd_user_info:699 | 蔺柯宇 | 0 |
| linqiang_supp | fnd_user_info:782 | 林强 | 1 |
| linxijie_ss | t_user:1000018 |  | 1 |
| linzhipeng_supp | fnd_user_info:736 | 林志鹏 | 1 |
| litao_supp | fnd_user_info:541 | 李涛 | 0 |
| liubingtong_ss | t_user:1000182 |  | 1 |
| liudawei_ss | t_user:1000202 |  | 1 |
| liudebang_supp | fnd_user_info:545 | 刘德邦 | 0 |
| liuhaibo_ss | t_user:1000008 |  | 1 |
| liuhu_supp | fnd_user_info:651 | 刘虎 | 0 |
| liujiazhen_supp | fnd_user_info:590 | 刘佳真 | 1 |
| liujie_ss | t_user:1000211 |  | 1 |
| liujun_ss | t_user:1000138 |  | 1 |
| liujunzhuo_supp | fnd_user_info:795 | 刘君焯 | 0 |
| liuliangliang_ss | t_user:1000180 |  | 1 |
| liuliping_ss | t_user:1000072 |  | 1 |
| liuqi_ss | t_user:1000080 |  | 1 |
| liuqian_ss | t_user:1000106 |  | 1 |
| liuweiwei_supp | fnd_user_info:768 | 刘威威 | 0 |
| liuxing_supp | fnd_user_info:794 | 刘兴 | 0 |
| liuxue_supp | fnd_user_info:798 | 刘雪 | 1 |
| liuying_ss | t_user:1000003 |  | 0 |
| liuyingzheng_supp | fnd_user_info:726 | 刘瀛正 | 1 |
| liuyong_ss | t_user:1000005 |  | 0 |
| liuzhuming_ss | t_user:1000011 |  | 1 |
| lixiao_ss | t_user:1000176 |  | 1 |
| liyadian_ss | t_user:1000029 |  | 1 |
| liyafan_ss | t_user:1000105 |  | 1 |
| liyaxian_supp | fnd_user_info:756 | 李娅娴 | 0 |
| liyongping_ss | t_user:1000212 |  | 1 |
| lizhao_ss | t_user:1000019 |  | 1 |
| lizhengpeng_supp | fnd_user_info:781 | 李正鹏 | 1 |
| lizhipeng_supp | fnd_user_info:800 | 李志鹏 | 1 |
| lnss_supp | fnd_user_info:512 | 辽宁三色 | 0 |
| longxiaozhu_ss | t_user:1000127 |  | 1 |
| luojing_ss | t_user:1000054 |  | 0 |
| luoquanzhe_supp | fnd_user_info:829 | 罗权柘 | 0 |
| luosidang_ss | t_user:1000167 |  | 0 |
| luotengfei_ss | t_user:1000169 |  | 0 |
| luoyueyun_ss | t_user:1000009 |  | 1 |
| luozhi_supp | fnd_user_info:807 | 罗智 | 1 |
| lupengpeng_supp | fnd_user_info:727 | 鲁鹏鹏 | 0 |
| lvjiacheng_supp | fnd_user_info:793 | 吕嘉诚 | 0 |
| machao_ss | t_user:1000122 |  | 1 |
| maibenjian_ss | t_user:1000208 |  | 1 |
| maijiahui_ss | t_user:1000178 |  | 1 |
| mawenzhe_supp | fnd_user_info:838 | 马文哲 | 1 |
| mengyutao_supp | fnd_user_info:818 | 孟玉涛 | 1 |
| miaochangyang_supp | fnd_user_info:711 | 苗畅洋 | 1 |
| miaotian_ss | t_user:1000119 |  | 1 |
| niushuren_supp | fnd_user_info:834 | 牛树仁 | 1 |
| njck_supp | fnd_user_info:522 | 南京川康 | 0 |
| njsc_supp | fnd_user_info:525 | 南京索诚 | 1 |
| njyn_supp | fnd_user_info:524 | 南京耀诺 | 1 |
| ouyangzhaofeng_supp | fnd_user_info:704 | 欧阳兆逢 | 1 |
| panchaozhong_ss | t_user:1000131 |  | 1 |
| pengjunjie_ss | t_user:1000015 |  | 1 |
| pms_tbpublic | fnd_user_info:473 | 技术公告Public | 1 |
| puyuhang_ss | t_user:1000069 |  | 0 |
| qiaojingbo_ss | t_user:1000028 |  | 1 |
| qiubaohui_ss | t_user:1000093 |  | 1 |
| qiurenwu_ss | t_user:1000166 |  | 2 |
| qiuzhenlu_supp | fnd_user_info:809 | 邱振禄 | 1 |
| raojie_ss | t_user:1000035 |  | 1 |
| renchangchao_supp | fnd_user_info:653 | 任长超 | 0 |
| ruanhao_supp | fnd_user_info:804 | 阮昊 | 0 |
| sczrxx_supp | fnd_user_info:519 | 四川智仁信息 | 0 |
| sdrk_supp | fnd_user_info:515 | 山东锐科 | 0 |
| shaoxinchao_supp | fnd_user_info:597 | 邵新超 | 0 |
| songjian_ss | t_user:1000130 |  | 1 |
| songrui_ss | t_user:1000160 |  | 1 |
| sse | t_user:1000000 |  | 1 |
| sse | fnd_user_info:785 | sse | 1 |
| suibensen_ss | t_user:1000216 |  | 1 |
| sunchaoran_supp | fnd_user_info:873 | 孙超然 | 1 |
| sunchengwu_supp | fnd_user_info:857 | 孙成武 | 1 |
| sunhaoran_ss | t_user:1000201 |  | 1 |
| sunruimin_supp | fnd_user_info:790 | 孙睿敏 | 1 |
| sunzhikui_ss | t_user:1000103 |  | 1 |
| suzhengxiong_supp | fnd_user_info:859 | 苏正雄 | 1 |
| sys | fnd_user_info:550 | 系统 | 1 |
| szbb_supp | fnd_user_info:517 | 北京神州邦邦 | 0 |
| taiqihang_supp | fnd_user_info:738 | 台啟杭 | 0 |
| tangchengyang_ss | t_user:1000199 |  | 1 |
| tangjiaming_ss | t_user:1000017 |  | 1 |
| tangle_ss | t_user:1000139 |  | 0 |
| tangxueke_ss | t_user:1000152 |  | 0 |
| tanjiawei_ss | t_user:1000123 |  | 1 |
| tanpengcheng_ss | t_user:1000013 |  | 0 |
| tanyang_supp | fnd_user_info:849 | 谭洋 | 1 |
| tengfarong_supp | fnd_user_info:721 | 滕发荣 | 1 |
| test_supp | fnd_user_info:506 | 测试外协 | 1 |
| test_supp1 | fnd_user_info:509 | 测试外协 | 1 |
| test_supp2 | fnd_user_info:508 | 测试外协 | 1 |
| test_supp3 | fnd_user_info:535 | 测试3 | 1 |
| tianguo_ss | t_user:1000087 |  | 1 |
| tianjunhui_supp | fnd_user_info:718 | 田军辉 | 1 |
| tianlifu_supp | fnd_user_info:546 | 田力夫 | 0 |
| tycw_supp | fnd_user_info:513 | 太原沧维 | 0 |
| wangchengyu_supp | fnd_user_info:616 | 王成宇 | 0 |
| wangchenxu_supp | fnd_user_info:729 | 汪晨旭 | 1 |
| wangchenyang_supp | fnd_user_info:775 | 汪晨阳 | 1 |
| wanghao_supp | fnd_user_info:697 | 王昊 | 1 |
| wangkang_supp | fnd_user_info:698 | 王康 | 1 |
| wanglei_supp | fnd_user_info:744 | 王蕾 | 1 |
| wangqinzheng_ss | t_user:1000004 |  | 1 |
| wangruixiang_ss | t_user:1000023 |  | 1 |
| wangxiaoyi_ss | t_user:1000066 |  | 1 |
| wangxinglu_ss | t_user:1000125 |  | 1 |
| wangxinwei_supp | fnd_user_info:840 | 王欣炜 | 1 |
| wangxiutao_supp | fnd_user_info:695 | 王秀涛 | 1 |
| wangxuan_supp | fnd_user_info:796 | 王轩 | 1 |
| wangxuesong_ss | t_user:1000007 |  | 1 |
| wangxuhui_ss | t_user:1000042 |  | 1 |
| wangye_supp | fnd_user_info:659 | 王烨 | 1 |
| wangyib_ss | t_user:1000200 |  | 1 |
| wangzhiheng_ss | t_user:1000060 |  | 0 |
| wangzhipeng_ss | t_user:1000189 |  | 1 |
| weichunting_ss | t_user:1000097 |  | 1 |
| wengencong_supp | fnd_user_info:884 | 翁恩聪 | 1 |
| wudexin_ss | t_user:1000210 |  | 1 |
| wuhao_ss | t_user:1000108 |  | 1 |
| wujiang_supp | fnd_user_info:821 | 吴疆 | 1 |
| wujingwen_ss | t_user:1000115 |  | 1 |
| wuzhiwen_ss | t_user:1000039 |  | 0 |
| wuzhongqi_supp | fnd_user_info:700 | 吴忠琦 | 1 |
| xiaheting_ss | t_user:1000024 |  | 1 |
| xialangguo_ss | t_user:1000070 |  | 0 |
| xialangguo_ss[U+00A0] | t_user:1000067 |  | 0 |
| xiaokun_supp | fnd_user_info:713 | 肖锟 | 1 |
| xiaoqinwen_ss | t_user:1000074 |  | 1 |
| xiaoyuanyuan_ss | t_user:1000032 |  | 0 |
| xiatongsen_supp | fnd_user_info:881 | 夏通森 | 1 |
| xieqianzhu_ss | t_user:1000100 |  | 1 |
| xingfubao_ss | t_user:1000194 |  | 1 |
| xiongqiang_ss | t_user:1000095 |  | 1 |
| xizhe_supp | fnd_user_info:844 | 席哲 | 0 |
| xufan_ss | t_user:1000026 |  | 0 |
| xuqiang_supp | fnd_user_info:816 | 徐强 | 1 |
| xushang_supp | fnd_user_info:657 | 徐尚 | 0 |
| xuxiangqian_ss | t_user:1000101 |  | 1 |
| xuzhenkui_supp | fnd_user_info:848 | 许振奎 | 1 |
| yangchen_ss | t_user:1000050 |  | 0 |
| yangdanni_ss | t_user:1000040 |  | 2 |
| yangde_supp | fnd_user_info:767 | 杨德 | 1 |
| yangkai_supp | fnd_user_info:544 | 杨凯 | 0 |
| yangkaitian_supp | fnd_user_info:815 | 杨凯添 | 0 |
| yangqingsong_ss | t_user:1000071 |  | 1 |
| yangshubo_supp | fnd_user_info:705 | 杨淑波 | 1 |
| yangsujin_supp | fnd_user_info:806 | 杨肃锦 | 1 |
| yangxing_supp | fnd_user_info:773 | 杨兴 | 1 |
| yangxingwen_ss | t_user:1000076 |  | 0 |
| yangyun_supp | fnd_user_info:805 | 杨云 | 1 |
| yangzeng_supp | fnd_user_info:553 | 杨增 | 1 |
| yanxinrui_supp | fnd_user_info:537 | 严鑫睿 | 1 |
| yanzuo_supp | fnd_user_info:843 | 晏座 | 0 |
| yejinliang_ss | t_user:1000036 |  | 1 |
| yewenshuo_supp | fnd_user_info:692 | 叶纹槊 | 1 |
| yezhihao_ss | t_user:1000043 |  | 1 |
| yinhailong_supp | fnd_user_info:706 | 尹海龙 | 1 |
| ysy_supp | fnd_user_info:514 | 山东亿数元 | 1 |
| yuqinqin_ss | t_user:1000164 |  | 0 |
| zcrh_supp | fnd_user_info:521 | 四川智诚瑞合 | 0 |
| zdkj_supp | fnd_user_info:520 | 成都泽达 | 0 |
| zengjian_supp | fnd_user_info:811 | 曾健 | 1 |
| zengzhipeng_ss | t_user:1000185 |  | 1 |
| zhangdongsheng_ss | t_user:1000155 |  | 0 |
| zhangguangyu_ss | t_user:1000179 |  | 1 |
| zhanghongfei_ss | t_user:1000099 |  | 1 |
| zhanghuidong_ss | t_user:1000170 |  | 1 |
| zhangjin_ss | t_user:1000132 |  | 1 |
| zhangjufeng_supp | fnd_user_info:758 | 张举峰 | 1 |
| zhanglele_supp | fnd_user_info:694 | 张乐乐 | 1 |
| zhangnan_supp | fnd_user_info:841 | 张楠 | 1 |
| zhangxizhen_ss | t_user:1000102 |  | 2 |
| zhangyongbing_supp | fnd_user_info:702 | 张永冰 | 1 |
| zhangyulong_supp | fnd_user_info:589 | 张雨龙 | 0 |
| zhangzhengxing_supp | fnd_user_info:799 | 张正兴 | 1 |
| zhaofangfang_ss | t_user:1000038 |  | 1 |
| zhaohongyu_supp | fnd_user_info:872 | 赵鸿宇 | 1 |
| zhaoyangxi_supp | fnd_user_info:862 | 赵洋希 | 1 |
| zhaozidu_supp | fnd_user_info:875 | 赵子都 | 1 |
| zhenghao_ss | t_user:1000117 |  | 0 |
| zhengjiaqi_ss | t_user:1000056 |  | 1 |
| zhengjienan_ss | t_user:1000051 |  | 0 |
| zhengjieyuan_supp | fnd_user_info:854 | 郑杰元 | 1 |
| zhengkang_supp | fnd_user_info:792 | 郑康 | 0 |
| zhengtao_supp | fnd_user_info:874 | 郑涛 | 1 |
| zhengzhiwen_ss | t_user:1000010 |  | 1 |
| zhonghaotian_supp | fnd_user_info:658 | 钟皓天 | 1 |
| zhouchao_supp | fnd_user_info:822 | 周超 | 1 |
| zhouduqun_ss | t_user:1000204 |  | 1 |
| zhouguangmin_supp | fnd_user_info:814 | 周广民 | 1 |
| zhoujun_ss | t_user:1000177 |  | 1 |
| zhouqitao_supp | fnd_user_info:600 | 周祺滔 | 1 |
| zhoutangfu_supp | fnd_user_info:751 | 周唐福 | 1 |
| zhouxinwei_supp | fnd_user_info:865 | 周欣伟 | 0 |
| zhouzisong_supp | fnd_user_info:864 | 周子淞 | 1 |
| zhujiangfeng_supp | fnd_user_info:754 | 朱江峰 | 0 |
| zhujie_supp | fnd_user_info:542 | 朱杰 | 0 |
| zhuxiaolong_ss | t_user:1000052 |  | 0 |
| zhuyunrong_ss | t_user:1000104 |  | 1 |
| zhuyuwei_ss | t_user:1000144 |  | 0 |
| zhuzexuan_ss | t_user:1000027 |  | 1 |
| zhuziyang_ss | t_user:1000215 |  | 1 |
| zoujin_supp | fnd_user_info:802 | 邹锦 | 1 |
| zoukaiping_ss | t_user:1000064 |  | 0 |
| zoupeng_ss | t_user:1000190 |  | 1 |
| zousonghe_ss | t_user:1000037 |  | 1 |
| zycx_supp | fnd_user_info:511 | 长春中宇创新 | 0 |
| 杨少毅 | fnd_user_info:390 | y04046 | 0 |
| 梁红玉 | fnd_user_info:228 | 梁红玉 | 0 |
| 白凯 | fnd_user_info:847 | b05761 | 0 |
| 胡慧东 | fnd_user_info:490 | h01564 | 0 |
| 赵炯粟 | fnd_user_info:309 | z03746 | 0 |

## 多公司主资料

来源 t_user:969（w02611）有两条任职记录 969、1000190；工号、公司、部门、岗位、手机、座机、性别、备注存在差异。任职数组按用户批准保留全部事实；账号单值主资料的选择仍需按可核实的原主任职处理，不能任取第一行。

没有关联账号的 t_user_info:1000186、1000188、1000189 继续列为待关联，不伪造账号。

## 最后五项用户字段与关联裁决

- 保留选中*_ss、*_supp原登录名；登录与账号维护输入兼容这些形式。注册规则不在此裁决范围。
- 旧admin不迁入账号资料，仅将历史创建/更新人关联当前admin ID1；不导入旧角色或授权；目标缺失或绑定改变时阻断。
- 用户明确例外：原值@dptech.com直接承接正式email，不置空、不改写、不用custom_info替代。普通新建/修改邮箱格式校验保持。
- 正式email仅去除来源尾部制表符，原值完整留来源证据。
- z04214、w04218、y04223、w04261、z04264迁入正式用户角色关系，原公司NULL留证据；不新增公司或办事处授权。

以上覆盖此前同项待裁决建议；来源原始记录不改写。代码接入及正式迁移结果另行核验，不代表已经落库。

## 当前执行边界（2026-10-09）

用户限定仅迁移首字母+工号、*_ss、*_supp，保留原字符串；t329条、fnd866条及330条任职合并1176个账号。登录/账号维护兼容已批准；旧admin仅99处历史审计引用关联当前admin ID1，不迁入资料、角色或范围。

实际原表解析确认1176个账号、1525个来源身份、823个可核实EHR工号；角色原关系t792条、fnd1140条。尚未部署或正式写入，迁移绑定、真实MySQL承接与页面验收仍须完成。
