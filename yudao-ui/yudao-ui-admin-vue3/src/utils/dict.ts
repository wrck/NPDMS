/**
 * 数据字典工具类
 */
import { useDictStoreWithOut } from '@/store/modules/dict'
import { ElementPlusInfoType } from '@/types/elementPlus'

const dictStore = useDictStoreWithOut()

/**
 * 获取 dictType 对应的数据字典数组
 *
 * @param dictType 数据类型
 * @returns {*|Array} 数据字典数组
 */
export interface DictDataType {
  dictType: string
  label: string
  value: string | number | boolean
  colorType: ElementPlusInfoType | ''
  cssClass: string
}

export interface NumberDictDataType extends DictDataType {
  value: number
}

export interface StringDictDataType extends DictDataType {
  value: string
}

export interface BooleanDictDataType extends DictDataType {
  value: boolean
}

export const getDictOptions = (dictType: string) => {
  return dictStore.getDictByType(dictType) || []
}

export const getIntDictOptions = (dictType: string): NumberDictDataType[] => {
  // 获得通用的 DictDataType 列表
  const dictOptions: DictDataType[] = getDictOptions(dictType)
  // 转换成 number 类型的 NumberDictDataType 类型
  // why 需要特殊转换：避免 IDEA 在 v-for="dict in getIntDictOptions(...)" 时，el-option 的 key 会告警
  const dictOption: NumberDictDataType[] = []
  dictOptions.forEach((dict: DictDataType) => {
    dictOption.push({
      ...dict,
      value: parseInt(dict.value + '')
    })
  })
  return dictOption
}

export const getStrDictOptions = (dictType: string) => {
  // 获得通用的 DictDataType 列表
  const dictOptions: DictDataType[] = getDictOptions(dictType)
  // 转换成 string 类型的 StringDictDataType 类型
  // why 需要特殊转换：避免 IDEA 在 v-for="dict in getStrDictOptions(...)" 时，el-option 的 key 会告警
  const dictOption: StringDictDataType[] = []
  dictOptions.forEach((dict: DictDataType) => {
    dictOption.push({
      ...dict,
      value: dict.value + ''
    })
  })
  return dictOption
}

export const getBoolDictOptions = (dictType: string): BooleanDictDataType[] => {
  const dictOption: BooleanDictDataType[] = []
  const dictOptions: DictDataType[] = getDictOptions(dictType)
  dictOptions.forEach((dict: DictDataType) => {
    dictOption.push({
      ...dict,
      value: dict.value + '' === 'true'
    })
  })
  return dictOption
}

/**
 * 获取指定字典类型的指定值对应的字典对象
 * @param dictType 字典类型
 * @param value 字典值
 * @return DictDataType 字典对象
 */
export const getDictObj = (dictType: string, value: any): DictDataType | undefined => {
  const dictOptions: DictDataType[] = getDictOptions(dictType)
  for (const dict of dictOptions) {
    if (dict.value === value + '') {
      return dict
    }
  }
}

/**
 * 获得字典数据的文本展示
 *
 * @param dictType 字典类型
 * @param value 字典数据的值
 * @return 字典名称
 */
export const getDictLabel = (dictType: string, value: any): string => {
  const dictOptions: DictDataType[] = getDictOptions(dictType)
  const dictLabel = ref('')
  dictOptions.forEach((dict: DictDataType) => {
    if (dict.value === value + '') {
      dictLabel.value = dict.label
    }
  })
  return dictLabel.value
}

export enum DICT_TYPE {
  USER_TYPE = 'user_type',
  COMMON_STATUS = 'common_status',
  TERMINAL = 'terminal', // 终端
  DATE_INTERVAL = 'date_interval', // 数据间隔

  // ========== SYSTEM 模块 ==========
  SYSTEM_USER_SEX = 'system_user_sex',
  SYSTEM_MENU_TYPE = 'system_menu_type',
  SYSTEM_ROLE_TYPE = 'system_role_type',
  SYSTEM_DATA_SCOPE = 'system_data_scope',
  SYSTEM_NOTICE_TYPE = 'system_notice_type',
  SYSTEM_LOGIN_TYPE = 'system_login_type',
  SYSTEM_LOGIN_RESULT = 'system_login_result',
  SYSTEM_SMS_CHANNEL_CODE = 'system_sms_channel_code',
  SYSTEM_SMS_TEMPLATE_TYPE = 'system_sms_template_type',
  SYSTEM_SMS_SEND_STATUS = 'system_sms_send_status',
  SYSTEM_SMS_RECEIVE_STATUS = 'system_sms_receive_status',
  SYSTEM_OAUTH2_GRANT_TYPE = 'system_oauth2_grant_type',
  SYSTEM_MAIL_SEND_STATUS = 'system_mail_send_status',
  SYSTEM_NOTIFY_TEMPLATE_TYPE = 'system_notify_template_type',
  SYSTEM_SOCIAL_TYPE = 'system_social_type',

  // ========== INFRA 模块 ==========
  INFRA_BOOLEAN_STRING = 'infra_boolean_string',
  INFRA_JOB_STATUS = 'infra_job_status',
  INFRA_JOB_LOG_STATUS = 'infra_job_log_status',
  INFRA_API_ERROR_LOG_PROCESS_STATUS = 'infra_api_error_log_process_status',
  INFRA_CONFIG_TYPE = 'infra_config_type',
  INFRA_CODEGEN_TEMPLATE_TYPE = 'infra_codegen_template_type',
  INFRA_CODEGEN_FRONT_TYPE = 'infra_codegen_front_type',
  INFRA_CODEGEN_SCENE = 'infra_codegen_scene',
  INFRA_FILE_STORAGE = 'infra_file_storage',
  INFRA_OPERATE_TYPE = 'infra_operate_type',

  // ========== BPM 模块 ==========
  BPM_MODEL_TYPE = 'bpm_model_type',
  BPM_MODEL_FORM_TYPE = 'bpm_model_form_type',
  BPM_TASK_CANDIDATE_STRATEGY = 'bpm_task_candidate_strategy',
  BPM_PROCESS_INSTANCE_STATUS = 'bpm_process_instance_status',
  BPM_TASK_STATUS = 'bpm_task_status',
  BPM_COMMENT_TYPE = 'bpm_comment_type',
  BPM_OA_LEAVE_TYPE = 'bpm_oa_leave_type',
  BPM_PROCESS_LISTENER_TYPE = 'bpm_process_listener_type',
  BPM_PROCESS_LISTENER_VALUE_TYPE = 'bpm_process_listener_value_type',

  // ========== PAY 模块 ==========
  PAY_CHANNEL_CODE = 'pay_channel_code', // 支付渠道编码类型
  PAY_ORDER_STATUS = 'pay_order_status', // 商户支付订单状态
  PAY_REFUND_STATUS = 'pay_refund_status', // 退款订单状态
  PAY_NOTIFY_STATUS = 'pay_notify_status', // 商户支付回调状态
  PAY_NOTIFY_TYPE = 'pay_notify_type', // 商户支付回调状态
  PAY_TRANSFER_TYPE = 'pay_transfer_type', // 转账订单类型
  PAY_TRANSFER_STATUS = 'pay_transfer_status', // 转账订单状态

  // ========== MP 模块 ==========
  MP_AUTO_REPLY_REQUEST_MATCH = 'mp_auto_reply_request_match', // 自动回复请求匹配类型
  MP_MESSAGE_TYPE = 'mp_message_type', // 消息类型

  // ========== Member 会员模块 ==========
  MEMBER_POINT_BIZ_TYPE = 'member_point_biz_type', // 积分的业务类型
  MEMBER_EXPERIENCE_BIZ_TYPE = 'member_experience_biz_type', // 会员经验业务类型

  // ========== MALL - 商品模块 ==========
  PRODUCT_SPU_STATUS = 'product_spu_status', //商品状态

  // ========== MALL - 交易模块 ==========
  EXPRESS_CHARGE_MODE = 'trade_delivery_express_charge_mode', //快递的计费方式
  TRADE_AFTER_SALE_STATUS = 'trade_after_sale_status', // 售后 - 状态
  TRADE_AFTER_SALE_WAY = 'trade_after_sale_way', // 售后 - 方式
  TRADE_AFTER_SALE_TYPE = 'trade_after_sale_type', // 售后 - 类型
  TRADE_ORDER_TYPE = 'trade_order_type', // 订单 - 类型
  TRADE_ORDER_STATUS = 'trade_order_status', // 订单 - 状态
  TRADE_ORDER_ITEM_AFTER_SALE_STATUS = 'trade_order_item_after_sale_status', // 订单项 - 售后状态
  TRADE_DELIVERY_TYPE = 'trade_delivery_type', // 配送方式
  BROKERAGE_ENABLED_CONDITION = 'brokerage_enabled_condition', // 分佣模式
  BROKERAGE_BIND_MODE = 'brokerage_bind_mode', // 分销关系绑定模式
  BROKERAGE_BANK_NAME = 'brokerage_bank_name', // 佣金提现银行
  BROKERAGE_WITHDRAW_TYPE = 'brokerage_withdraw_type', // 佣金提现类型
  BROKERAGE_RECORD_BIZ_TYPE = 'brokerage_record_biz_type', // 佣金业务类型
  BROKERAGE_RECORD_STATUS = 'brokerage_record_status', // 佣金状态
  BROKERAGE_WITHDRAW_STATUS = 'brokerage_withdraw_status', // 佣金提现状态

  // ========== MALL - 营销模块 ==========
  PROMOTION_DISCOUNT_TYPE = 'promotion_discount_type', // 优惠类型
  PROMOTION_PRODUCT_SCOPE = 'promotion_product_scope', // 营销的商品范围
  PROMOTION_COUPON_TEMPLATE_VALIDITY_TYPE = 'promotion_coupon_template_validity_type', // 优惠劵模板的有限期类型
  PROMOTION_COUPON_STATUS = 'promotion_coupon_status', // 优惠劵的状态
  PROMOTION_COUPON_TAKE_TYPE = 'promotion_coupon_take_type', // 优惠劵的领取方式
  PROMOTION_CONDITION_TYPE = 'promotion_condition_type', // 营销的条件类型枚举
  PROMOTION_BARGAIN_RECORD_STATUS = 'promotion_bargain_record_status', // 砍价记录的状态
  PROMOTION_COMBINATION_RECORD_STATUS = 'promotion_combination_record_status', // 拼团记录的状态
  PROMOTION_BANNER_POSITION = 'promotion_banner_position', // banner 定位

  // ========== CRM - 客户管理模块 ==========
  CRM_AUDIT_STATUS = 'crm_audit_status', // CRM 审批状态
  CRM_BIZ_TYPE = 'crm_biz_type', // CRM 业务类型
  CRM_BUSINESS_END_STATUS_TYPE = 'crm_business_end_status_type', // CRM 商机结束状态类型
  CRM_RECEIVABLE_RETURN_TYPE = 'crm_receivable_return_type', // CRM 回款的还款方式
  CRM_CUSTOMER_INDUSTRY = 'crm_customer_industry', // CRM 客户所属行业
  CRM_CUSTOMER_LEVEL = 'crm_customer_level', // CRM 客户级别
  CRM_CUSTOMER_SOURCE = 'crm_customer_source', // CRM 客户来源
  CRM_PRODUCT_STATUS = 'crm_product_status', // CRM 商品状态
  CRM_PERMISSION_LEVEL = 'crm_permission_level', // CRM 数据权限的级别
  CRM_PRODUCT_UNIT = 'crm_product_unit', // CRM 产品单位
  CRM_FOLLOW_UP_TYPE = 'crm_follow_up_type', // CRM 跟进方式

  // ========== ERP - 企业资源计划模块  ==========
  ERP_AUDIT_STATUS = 'erp_audit_status', // ERP 审批状态
  ERP_STOCK_RECORD_BIZ_TYPE = 'erp_stock_record_biz_type', // 库存明细的业务类型

  // ========== WMS - 仓库管理模块 ==========
  WMS_MERCHANT_TYPE = 'merchant_type', // WMS 往来企业类型
  WMS_ORDER_TYPE = 'wms_order_type', // WMS 单据类型
  WMS_ORDER_STATUS = 'wms_order_status', // WMS 单据状态
  WMS_RECEIPT_ORDER_TYPE = 'wms_receipt_order_type', // WMS 入库单类型
  WMS_SHIPMENT_ORDER_TYPE = 'wms_shipment_order_type', // WMS 出库单类型

  // ========== AI - 人工智能模块  ==========
  AI_PLATFORM = 'ai_platform', // AI 平台
  AI_MODEL_TYPE = 'ai_model_type', // AI 模型类型
  AI_IMAGE_STATUS = 'ai_image_status', // AI 图片状态
  AI_MUSIC_STATUS = 'ai_music_status', // AI 音乐状态
  AI_GENERATE_MODE = 'ai_generate_mode', // AI 生成模式
  AI_WRITE_TYPE = 'ai_write_type', // AI 写作类型
  AI_WRITE_LENGTH = 'ai_write_length', // AI 写作长度
  AI_WRITE_FORMAT = 'ai_write_format', // AI 写作格式
  AI_WRITE_TONE = 'ai_write_tone', // AI 写作语气
  AI_WRITE_LANGUAGE = 'ai_write_language', // AI 写作语言
  AI_MCP_CLIENT_NAME = 'ai_mcp_client_name', // AI MCP Client 名字

  // ========== IOT - 物联网模块  ==========
  IOT_NET_TYPE = 'iot_net_type', // IOT 联网方式
  IOT_PRODUCT_STATUS = 'iot_product_status', // IOT 产品状态
  IOT_PRODUCT_DEVICE_TYPE = 'iot_product_device_type', // IOT 产品设备类型
  IOT_PROTOCOL_TYPE = 'iot_protocol_type', // IOT 协议类型
  IOT_SERIALIZE_TYPE = 'iot_serialize_type', // IOT 序列化类型
  IOT_LOCATION_TYPE = 'iot_location_type', // IOT 定位类型
  IOT_DEVICE_STATE = 'iot_device_state', // IOT 设备状态
  IOT_THING_MODEL_TYPE = 'iot_thing_model_type', // IOT 产品功能类型
  IOT_THING_MODEL_UNIT = 'iot_thing_model_unit', // IOT 物模型单位
  IOT_RW_TYPE = 'iot_rw_type', // IOT 读写类型
  // TODO 貌似这几个多了 _enum 后缀
  IOT_DATA_SINK_TYPE_ENUM = 'iot_data_sink_type_enum', // IoT 数据流转目的类型
  IOT_RULE_SCENE_TRIGGER_TYPE_ENUM = 'iot_rule_scene_trigger_type_enum', // IoT 场景流转的触发类型枚举
  IOT_RULE_SCENE_ACTION_TYPE_ENUM = 'iot_rule_scene_action_type_enum', // IoT 规则场景的触发类型枚举
  IOT_ALERT_LEVEL = 'iot_alert_level', // IoT 告警级别
  IOT_ALERT_RECEIVE_TYPE = 'iot_alert_receive_type', // IoT 告警接收类型
  IOT_OTA_TASK_DEVICE_SCOPE = 'iot_ota_task_device_scope', // IoT OTA任务设备范围
  IOT_OTA_TASK_STATUS = 'iot_ota_task_status', // IoT OTA 任务状态
  IOT_OTA_TASK_RECORD_STATUS = 'iot_ota_task_record_status', // IoT OTA 记录状态
  IOT_MODBUS_MODE = 'iot_modbus_mode', // IoT Modbus 工作模式
  IOT_MODBUS_FRAME_FORMAT = 'iot_modbus_frame_format', // IoT Modbus 帧格式

  // ========== MES - 制造执行系统模块  ==========
  MES_MD_ITEM_OR_PRODUCT = 'mes_md_item_or_product', // MES 物料产品标识
  MES_CLIENT_TYPE = 'mes_client_type', // MES 客户类型
  MES_VENDOR_LEVEL = 'mes_vendor_level', // MES 供应商级别
  MES_CAL_HOLIDAY_TYPE = 'mes_cal_holiday_type', // MES 假期类型
  MES_CAL_SHIFT_TYPE = 'mes_cal_shift_type', // MES 轮班方式
  MES_CAL_SHIFT_METHOD = 'mes_cal_shift_method', // MES 倒班方式
  MES_CAL_CALENDAR_TYPE = 'mes_cal_calendar_type', // MES 班组类型
  MES_CAL_PLAN_STATUS = 'mes_cal_plan_status', // MES 排班计划状态
  MES_TM_TOOL_STATUS = 'mes_tm_tool_status', // MES 工具状态
  MES_TM_MAINTEN_TYPE = 'mes_tm_mainten_type', // MES 保养维护类型
  MES_DV_MACHINERY_STATUS = 'mes_dv_machinery_status', // MES 设备状态
  MES_DV_SUBJECT_TYPE = 'mes_dv_subject_type', // MES 点检保养项目类型
  MES_INDICATOR_TYPE = 'mes_indicator_type', // MES 检测项类型
  MES_QC_RESULT_TYPE = 'mes_qc_result_type', // MES 质检结果值类型
  MES_DEFECT_LEVEL = 'mes_defect_level', // MES 缺陷等级
  MES_PRO_WORK_ORDER_STATUS = 'mes_pro_work_order_status', // MES 生产工单状态
  MES_PRO_WORK_ORDER_SOURCE_TYPE = 'mes_pro_work_order_source_type', // MES 工单来源类型
  MES_PRO_WORK_ORDER_TYPE = 'mes_pro_work_order_type', // MES 工单类型
  MES_QC_TYPE = 'mes_qc_type', // MES 质检方案类型
  MES_PRO_LINK_TYPE = 'mes_pro_link_type', // MES 工序关系类型
  MES_PRO_TASK_STATUS = 'mes_pro_task_status', // MES 生产任务状态
  MES_TIME_UNIT_TYPE = 'mes_time_unit_type', // MES 时间单位
  MES_ORDER_STATUS = 'mes_order_status', // MES 单据状态
  MES_QC_CHECK_RESULT = 'mes_qc_check_result', // MES 检测结果
  MES_QC_SOURCE_DOC_TYPE = 'mes_qc_source_doc_type', // MES 来源单据类型
  MES_IPQC_TYPE = 'mes_ipqc_type', // MES IPQC 检验类型
  MES_DV_CYCLE_TYPE = 'mes_dv_cycle_type', // MES 点检保养周期类型
  MES_DV_CHECK_PLAN_STATUS = 'mes_dv_check_plan_status', // MES 点检保养方案状态
  MES_MAINTEN_RECORD_STATUS = 'mes_mainten_record_status', // MES 保养记录状态
  MES_MAINTEN_STATUS = 'mes_mainten_status', // MES 保养结果
  MES_DV_REPAIR_STATUS = 'mes_dv_repair_status', // MES 维修工单状态
  MES_DV_REPAIR_RESULT = 'mes_dv_repair_result', // MES 维修结果
  MES_DV_CHECK_RECORD_STATUS = 'mes_dv_check_record_status', // MES 点检记录状态
  MES_DV_CHECK_RESULT = 'mes_dv_check_result', // MES 点检结果
  MES_PRO_FEEDBACK_STATUS = 'mes_pro_feedback_status', // MES 生产报工状态
  MES_PRO_FEEDBACK_TYPE = 'mes_pro_feedback_type', // MES 生产报工类型
  MES_PRO_FEEDBACK_CHANNEL = 'mes_pro_feedback_channel', // MES 生产报工途径
  MES_PRO_ANDON_STATUS = 'mes_pro_andon_status', // MES 安灯处置状态
  MES_PRO_ANDON_LEVEL = 'mes_pro_andon_level', // MES 安灯级别
  MES_PRO_WORK_RECORD_TYPE = 'mes_pro_work_record_type', // MES 上下工状态类型
  MES_RQC_TYPE = 'mes_rqc_type', // MES 退货检验类型
  MES_WM_ARRIVAL_NOTICE_STATUS = 'mes_wm_arrival_notice_status', // MES 到货通知单状态
  MES_WM_ITEM_RECEIPT_STATUS = 'mes_wm_item_receipt_status', // MES 物料接收单状态
  MES_WM_TRANSFER_STATUS = 'mes_wm_transfer_status', // MES 转移单状态
  MES_WM_TRANSFER_TYPE = 'mes_wm_transfer_type', // MES 转移单类型
  MES_WM_STOCK_TAKING_TYPE = 'mes_wm_stock_taking_type', // MES 盘点类型
  MES_WM_STOCK_TAKING_TASK_STATUS = 'mes_wm_stock_taking_task_status', // MES 盘点任务状态
  MES_WM_STOCK_TAKING_LINE_STATUS = 'mes_wm_stock_taking_task_line_status', // MES 盘点任务行状态
  MES_WM_STOCK_TAKING_PLAN_PARAM_TYPE = 'mes_wm_stock_taking_plan_param_type', // MES 盘点方案参数类型
  MES_WM_OUTSOURCE_RECPT_STATUS = 'mes_wm_outsource_recpt_status', // MES 外协入库单状态
  MES_WM_PRODUCT_ISSUE_STATUS = 'mes_wm_product_issue_status', // MES 领料出库单状态
  MES_WM_PRODUCT_PRODUCE_STATUS = 'mes_wm_product_produce_status', // MES 生产入库单状态
  MES_WM_RETURN_VENDOR_STATUS = 'mes_wm_return_vendor_status', // MES 供应商退货单状态
  MES_WM_QUALITY_STATUS = 'mes_wm_quality_status', // MES 质量状态
  MES_WM_RETURN_ISSUE_STATUS = 'mes_wm_return_issue_status', // MES 生产退料单状态
  MES_WM_RETURN_ISSUE_TYPE = 'mes_wm_return_issue_type', // MES 退料类型
  MES_WM_PRODUCT_RECPT_STATUS = 'mes_wm_product_receipt_status', // MES 成品入库单状态
  MES_WM_RETURN_SALES_STATUS = 'mes_wm_return_sales_status', // MES 销售退货单状态
  MES_WM_PRODUCT_SALES_STATUS = 'mes_wm_product_sales_status', // MES 销售出库单状态
  MES_WM_SALES_NOTICE_STATUS = 'mes_wm_sales_notice_status', // MES 发货通知单状态
  MES_WM_MISC_ISSUE_TYPE = 'mes_wm_misc_issue_type', // MES 杂项出库类型
  MES_WM_MISC_ISSUE_STATUS = 'mes_wm_misc_issue_status', // MES 杂项出库单状态
  MES_WM_MISC_RECEIPT_TYPE = 'mes_wm_misc_receipt_type', // MES 杂项单类型
  MES_WM_MISC_RECEIPT_STATUS = 'mes_wm_misc_receipt_status', // MES 杂项入库单状态
  MES_WM_OUTSOURCE_RECEIPT_STATUS = 'mes_wm_outsource_receipt_status', // MES 外协入库单状态
  MES_WM_OUTSOURCE_ISSUE_STATUS = 'mes_wm_outsource_issue_status', // MES 外协出库单状态
  MES_MD_AUTO_CODE_PART_TYPE = 'mes_md_auto_code_part_type', // MES 编码规则分段类型
  MES_MD_AUTO_CODE_PADDED_METHOD = 'mes_md_auto_code_padded_method', // MES 编码规则补齐方式
  MES_MD_AUTO_CODE_CYCLE_METHOD = 'mes_md_auto_code_cycle_method', // MES 编码规则循环方式
  MES_WM_BARCODE_FORMAT = 'mes_wm_barcode_format', // MES 条码格式
  MES_WM_BARCODE_BIZ_TYPE = 'mes_wm_barcode_biz_type', // MES 条码业务类型
  MES_WM_PACKAGE_STATUS = 'mes_wm_package_status', // MES 装箱单状态

  // ========== IM - 即时通讯模块  ==========
  IM_CONTENT_TYPE = 'im_content_type', // IM 内容类型
  IM_MESSAGE_STATUS = 'im_message_status', // IM 消息状态：0=正常 / 2=已撤回（私聊 / 群聊共用）
  IM_MESSAGE_RECEIPT_STATUS = 'im_message_receipt_status', // IM 消息回执状态：0=不需要 / 1=待完成 / 2=已完成
  IM_FRIEND_STATUS = 'im_friend_status', // IM 好友状态
  IM_FRIEND_ADD_SOURCE = 'im_friend_add_source', // IM 好友添加来源
  IM_FRIEND_REQUEST_HANDLE_RESULT = 'im_friend_request_handle_result', // IM 好友申请处理结果
  IM_GROUP_STATUS = 'im_group_status', // IM 群状态
  IM_GROUP_MEMBER_ROLE = 'im_group_member_role', // IM 群成员角色
  IM_GROUP_ADD_SOURCE = 'im_group_add_source', // IM 加群来源
  IM_GROUP_REQUEST_HANDLE_RESULT = 'im_group_request_handle_result', // IM 加群申请处理结果
  IM_RTC_CALL_MEDIA_TYPE = 'im_rtc_call_media_type', // IM 通话媒体类型：1=语音 / 2=视频
  IM_RTC_CALL_CONVERSATION_TYPE = 'im_rtc_call_conversation_type', // IM 通话会话类型：1=私聊 / 2=群聊
  IM_RTC_CALL_STATUS = 'im_rtc_call_status', // IM 通话状态：10=创建 / 20=进行中 / 30=已结束
  IM_RTC_CALL_END_REASON = 'im_rtc_call_end_reason', // IM 通话结束原因：1=通话结束 / 2=已拒绝 / 3=已取消 / 4=无人接听 / 5=对方正忙 / 9=通话异常
  IM_RTC_PARTICIPANT_ROLE = 'im_rtc_participant_role', // IM 通话参与角色：1=发起人 / 2=被邀请者 / 3=主动加入者
  IM_RTC_PARTICIPANT_STATUS = 'im_rtc_participant_status', // IM 通话参与状态：10=邀请中 / 20=已加入 / 30=已拒绝 / 40=未应答 / 50=已离开
  IM_CHANNEL_MATERIAL_TYPE = 'im_channel_material_type', // IM 频道素材内容类型：1=富文本 / 2=外链

  // ========== PMS - 项目交付管理模块 ==========
  PMS_PROJECT_STATUS = 'pms_project_status', // 项目状态
  PMS_PROJECT_CATEGORY = 'pms_project_category', // 项目分类
  PMS_SIGNING_METHOD = 'pms_signing_method', // 模板匹配维度：签约方式
  PMS_IMPLEMENTATION_METHOD = 'pms_implementation_method', // 模板匹配维度：实施方式
  PMS_MAJOR_PROJECT_LEVEL = 'pms_major_project_level', // 模板匹配维度：重大项目级别（CRM来源映射）
  PMS_PROJECT_MEMBER_ROLE = 'pms_project_member_role', // 项目成员角色（F-PM01 V57）
  PMS_PROJECT_LIFECYCLE_STAGE = 'pms_project_lifecycle_stage', // 项目生命周期阶段 S0~S6/MAINT（F-PM01 V57）
  PMS_TEMPLATE_LOAD_METHOD = 'pms_template_load_method', // 模板加载方式（F-PM01 V57）
  PMS_PROJECT_STAGE_STATUS = 'pms_project_stage_status', // 阶段实例状态（F-PM01 V57）
  PMS_PROJECT_TASK_STATUS = 'pms_project_task_status', // 任务实例状态（F-PM01 V57）
  PMS_PROJECT_MILESTONE_STATUS = 'pms_project_milestone_status', // 里程碑实例状态（F-PM01 V57）
  PMS_PROJECT_DELIVERABLE_STATUS = 'pms_project_deliverable_status', // 交付件实例状态（F-PM01 V57）
  PMS_PROJECT_GATE_STATUS = 'pms_project_gate_status', // 门禁实例状态（F-PM01 V57）
  PMS_PROJECT_SOURCE_TYPE = 'pms_project_source_type', // 项目创建来源（F-PM01 V57）
  PMS_TASK_STATUS = 'pms_task_status', // 任务WBS状态
  PMS_RISK_LEVEL = 'pms_risk_level', // 项目风险等级
  PMS_PLAN_CHANGE_STATUS = 'pms_plan_change_status', // 计划变更状态
  PMS_DELIVERABLE_TYPE = 'pms_deliverable_type', // 交付件类型
  PMS_DELIVERABLE_STATUS = 'pms_deliverable_status', // 交付件状态
  PMS_ANNOUNCEMENT_TYPE = 'pms_announcement_type', // 公告类型
  PMS_AUTHORIZATION_TYPE = 'pms_authorization_type', // 授权类型
  PMS_BRIEFING_TYPE = 'pms_briefing_type', // 交底类型
  PMS_PRODUCT_TYPE = 'pms_product_type', // 表单模板产品类型
  PMS_FORM_TEMPLATE_STATUS = 'pms_form_template_status', // 表单模板状态
  PMS_FORM_INSTANCE_STATUS = 'pms_form_instance_status', // 表单实例状态
  PMS_ISSUE_SEVERITY = 'pms_issue_severity', // 问题严重等级
  PMS_ISSUE_STATUS = 'pms_issue_status', // 问题状态
  PMS_ENG_STATUS = 'pms_eng_status', // 工程通用状态
  PMS_CUTOVER_TASK_STATUS = 'pms_cutover_task_status', // 割接任务状态
  PMS_CUTOVER_PLAN_STATUS = 'pms_cutover_plan_status', // 割接方案状态
  PMS_CUTOVER_TYPE = 'pms_cutover_type', // 割接类型
  PMS_NETWORK_MODE = 'pms_network_mode', // 组网模式
  PMS_SOURCE_TYPE = 'pms_source_type', // 来源类型
  PMS_CUTOVER_RISK_TYPE = 'pms_cutover_risk_type', // 割接风险类型
  PMS_CUTOVER_RISK_STATUS = 'pms_cutover_risk_status', // 割接风险状态
  PMS_CUTOVER_EXEC_STATUS = 'pms_cutover_exec_status', // 割接执行状态
  PMS_CUTOVER_OBSERVATION_STATUS = 'pms_cutover_observation_status', // 割接观察状态
  PMS_LEFTOVER_STATUS = 'pms_leftover_status', // 遗留状态
  PMS_ACCEPTANCE_TYPE = 'pms_acceptance_type', // 验收类型
  PMS_DOCUMENT_TYPE = 'pms_document_type', // 文档类型
  PMS_ACCEPTANCE_STATUS = 'pms_acceptance_status', // 验收状态
  PMS_INSPECTION_MODE = 'pms_inspection_mode', // 巡检模式
  PMS_SRV_TASK_STATUS = 'pms_srv_task_status', // 巡检任务状态
  PMS_SRV_RULE_TYPE = 'pms_srv_rule_type', // 巡检规则类型
  PMS_SRV_RULE_STATUS = 'pms_srv_rule_status', // 巡检规则状态
  PMS_SRV_REPORT_TYPE = 'pms_srv_report_type', // 巡检报告类型
  PMS_SRV_REPORT_STATUS = 'pms_srv_report_status', // 巡检报告状态
  PMS_SRV_ISSUE_SEVERITY = 'pms_srv_issue_severity', // 巡检问题严重等级
  PMS_SRV_ISSUE_STATUS = 'pms_srv_issue_status', // 巡检问题状态
  PMS_SRV_MAINTENANCE_STATUS = 'pms_srv_maintenance_status', // 维保状态
  PMS_SERVICE_LEVEL = 'pms_service_level', // 服务等级
  PMS_EQUIPMENT_STATUS = 'pms_equipment_status', // 设备状态
  PMS_BATCH_CHANGE_STATUS = 'pms_batch_change_status', // 批量变更状态
  PMS_APPROVAL_STATUS = 'pms_approval_status', // 审批状态(通用7态)
  PMS_REVIEW_LEVEL = 'pms_review_level', // 评审级别
  PMS_JOINT_TEST_STATUS = 'pms_joint_test_status', // 联调状态
  PMS_MATERIAL_EXCH_TYPE = 'pms_material_exch_type', // 换货类型
  PMS_CRM_SYNC_STATUS = 'pms_crm_sync_status', // CRM同步状态
  PMS_SITE_SURVEY_STATUS = 'pms_site_survey_status', // 工勘状态
  PMS_PORTFOLIO_STATUS = 'pms_portfolio_status', // 项目组合状态
  PMS_GOVERNANCE_STATUS = 'pms_governance_status', // 治理动作状态
  PMS_MAINT_TRANSITION_STATUS = 'pms_maint_transition_status', // 转维保状态
  PMS_COMPLETION_CERT_STATUS = 'pms_completion_cert_status', // 完工证明状态
  PMS_CLOSURE_STATUS = 'pms_closure_status', // 项目闭环状态
  PMS_SCHEDULE_STATUS = 'pms_schedule_status', // 倒排计划状态
  PMS_PLAN_CHANGE_TYPE = 'pms_plan_change_type', // 计划变更类型
  PMS_ARRIVAL_STATUS = 'pms_arrival_status', // 到货签收状态
  PMS_BRIEFING_STATUS = 'pms_briefing_status', // 交底状态
  PMS_ANN_CHECK_STATUS = 'pms_ann_check_status', // 公告检查状态
  PMS_ANNOUNCEMENT_STATUS = 'pms_announcement_status', // 公告状态
  PMS_REQUIREMENT_STATUS = 'pms_requirement_status', // 需求状态
  PMS_RESOURCE_STATUS = 'pms_resource_status', // 资源就绪状态
  PMS_ENG_RISK_STATUS = 'pms_eng_risk_status', // 工程风险状态
  PMS_DOC_TEMPLATE_STATUS = 'pms_doc_template_status', // 文档模板状态
  PMS_EXT_PROC_TYPE = 'pms_ext_proc_type', // 外采类型
  PMS_MATERIAL_REQ_TYPE = 'pms_material_req_type', // 物料申请类型
  PMS_OUTSOURCE_TYPE = 'pms_outsource_type', // 外协类型
  PMS_ENG_RISK_TYPE = 'pms_eng_risk_type', // 工程风险类型
  PMS_ANNOUNCEMENT_SEVERITY = 'pms_announcement_severity', // 公告严重等级
  PMS_ANN_CHECK_MATCH = 'pms_ann_check_match', // 公告命中结果
  PMS_DOC_CATEGORY = 'pms_doc_category', // 文档分类
  PMS_ENG_RISK_LEVEL = 'pms_eng_risk_level', // 工程风险等级
  PMS_EOM_TYPE = 'pms_eom_type', // 生命周期标识
  PMS_TRIGGER_SOURCE = 'pms_trigger_source', // 触发来源
  PMS_STOCK_STATUS = 'pms_stock_status', // 库存状态
  PMS_RESOURCE_TYPE = 'pms_resource_type', // 资源类型
  PMS_PROJECT_RISK_LEVEL = 'pms_project_risk_level', // 项目风险等级(高/中/低)
  PMS_PROJECT_RISK_STATUS = 'pms_project_risk_status', // 项目风险状态(已识别/处理中/已关闭/已发生)
  PMS_PROJECT_PHASE_STATUS = 'pms_project_phase_status', // 项目阶段状态(未开始/进行中/已完成/已跳过)
  PMS_ISSUE_SOURCE = 'pms_issue_source', // 实施问题来源(安装/配置/联调/其他)
  PMS_PORTFOLIO_TYPE = 'pms_portfolio_type', // 项目组合类型(静态/动态)
  PMS_PORTFOLIO_CATEGORY = 'pms_portfolio_category', // 项目组合分类(战略/客户/区域/计划/专项)
  PMS_PORTFOLIO_RULE_DIMENSION = 'pms_portfolio_rule_dimension', // 组合规则维度
  PMS_PORTFOLIO_RULE_OPERATOR = 'pms_portfolio_rule_operator', // 组合规则操作符
  PMS_REQUIREMENT_TYPE = 'pms_requirement_type', // 需求类型(业务需求/接口规划)
  PMS_GOVERNANCE_ACTION_TYPE = 'pms_governance_action_type', // 治理动作类型
  PMS_CURRENCY = 'pms_currency', // 货币类型
  PMS_PROJECT_TYPE = 'pms_project_type' // 项目类型
}
