-- 割接专项 V1/V2：完整标准配置及独立示例草稿。只新增，不改写历史发布版本。

-- 依据 docs/baseline/prd-v1.8.md CUT-07/09/10 及修订008。97项由原始复合检查拆分为独立作答项。

-- 拆分来源：0807表3.2第12/15/33/71/93行；每项保留原名称、行号和说明，不引入新的检查阈值。

-- 普通风险24项 + 双机检查97项覆盖基线25类风险；核心调研12类。

-- 新任务字典独立于废弃入口字典，保留旧值及旧入口。样例草稿覆盖精确、部分限定、优先级覆盖、D级无匹配和停用不参与。



INSERT INTO system_dict_type (id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time)
SELECT 970442000,'PMS-割接任务类型','pms_cutover_task_type',0,'割接专项正式字典','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_cutover_task_type' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442101,1,'设备替换（原厂新旧整机）','DEVICE_REPLACE_WHOLE','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='DEVICE_REPLACE_WHOLE' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442102,2,'设备替换（原厂新旧板卡）','DEVICE_REPLACE_BOARD','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='DEVICE_REPLACE_BOARD' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442103,3,'设备替换（替换友商设备）','DEVICE_REPLACE_VENDOR','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='DEVICE_REPLACE_VENDOR' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442104,4,'设备入网（新开局）','DEVICE_ONBOARD','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='DEVICE_ONBOARD' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442105,5,'版本升级','VERSION_UPGRADE','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='VERSION_UPGRADE' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442106,6,'灾备演练','DISASTER_RECOVERY_DRILL','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='DISASTER_RECOVERY_DRILL' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442107,7,'配置变更','CONFIGURATION_CHANGE','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='CONFIGURATION_CHANGE' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442108,8,'网络结构调整','NETWORK_TOPOLOGY_CHANGE','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='NETWORK_TOPOLOGY_CHANGE' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442109,9,'版本补丁','VERSION_PATCH','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='VERSION_PATCH' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442110,10,'特征库升级','SIGNATURE_UPGRADE','pms_cutover_task_type',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_type' AND value='SIGNATURE_UPGRADE' AND deleted=b'0');

INSERT INTO system_dict_type (id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time)
SELECT 970442001,'PMS-割接任务组网模式','pms_cutover_task_network_mode',0,'割接专项正式字典','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_cutover_task_network_mode' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442131,1,'VSM双机','VSM','pms_cutover_task_network_mode',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_network_mode' AND value='VSM' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442132,2,'静默双机','SILENT_DUAL','pms_cutover_task_network_mode',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_network_mode' AND value='SILENT_DUAL' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442133,3,'DRP双机','DRP_DUAL','pms_cutover_task_network_mode',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_network_mode' AND value='DRP_DUAL' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442134,4,'普通双机','NORMAL_DUAL','pms_cutover_task_network_mode',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_network_mode' AND value='NORMAL_DUAL' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442135,5,'集群','CLUSTER','pms_cutover_task_network_mode',0,'primary','','基线CUT-07','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_cutover_task_network_mode' AND value='CLUSTER' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
SELECT 970442199,4,'DPX框式设备','DPX','pms_device_type',0,'primary','','基线CUT-09明确引用的设备类型','cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_device_type' AND value='DPX' AND deleted=b'0');

-- BEGIN CUTOVER CONFIGURATION JSON
SET @cut_seed = '{
  "dictionarySnapshot": {
    "pms_cutover_task_type": [
      {
        "value": "DEVICE_REPLACE_WHOLE",
        "label": "设备替换（原厂新旧整机）"
      },
      {
        "value": "DEVICE_REPLACE_BOARD",
        "label": "设备替换（原厂新旧板卡）"
      },
      {
        "value": "DEVICE_REPLACE_VENDOR",
        "label": "设备替换（替换友商设备）"
      },
      {
        "value": "DEVICE_ONBOARD",
        "label": "设备入网（新开局）"
      },
      {
        "value": "VERSION_UPGRADE",
        "label": "版本升级"
      },
      {
        "value": "DISASTER_RECOVERY_DRILL",
        "label": "灾备演练"
      },
      {
        "value": "CONFIGURATION_CHANGE",
        "label": "配置变更"
      },
      {
        "value": "NETWORK_TOPOLOGY_CHANGE",
        "label": "网络结构调整"
      },
      {
        "value": "VERSION_PATCH",
        "label": "版本补丁"
      },
      {
        "value": "SIGNATURE_UPGRADE",
        "label": "特征库升级"
      }
    ],
    "pms_cutover_task_network_mode": [
      {
        "value": "VSM",
        "label": "VSM双机"
      },
      {
        "value": "SILENT_DUAL",
        "label": "静默双机"
      },
      {
        "value": "DRP_DUAL",
        "label": "DRP双机"
      },
      {
        "value": "NORMAL_DUAL",
        "label": "普通双机"
      },
      {
        "value": "CLUSTER",
        "label": "集群"
      }
    ],
    "pms_device_type": [
      {
        "value": "FW",
        "label": "防火墙"
      },
      {
        "value": "SW",
        "label": "交换机"
      },
      {
        "value": "ADX",
        "label": "应用交付设备"
      },
      {
        "value": "DPX",
        "label": "DPX框式设备"
      }
    ],
    "pms_risk_level": [
      {
        "value": "A",
        "label": "A级"
      },
      {
        "value": "B",
        "label": "B级"
      },
      {
        "value": "C",
        "label": "C级"
      },
      {
        "value": "D",
        "label": "D级"
      }
    ]
  },
  "dimensions": [
    {
      "code": "CUTOVER_TYPE",
      "name": "割接类型",
      "dataType": "STRING",
      "valueSource": "DICT:pms_cutover_task_type",
      "owner": "CUT",
      "contextPath": "task.cutoverType",
      "enabled": true
    },
    {
      "code": "NETWORK_MODE",
      "name": "组网模式",
      "dataType": "STRING",
      "valueSource": "DICT:pms_cutover_task_network_mode",
      "owner": "CUT",
      "contextPath": "task.networkMode",
      "enabled": true
    },
    {
      "code": "DEVICE_TYPE",
      "name": "设备类型",
      "dataType": "STRING",
      "valueSource": "DICT:pms_device_type",
      "owner": "SYSTEM",
      "contextPath": "task.deviceType",
      "enabled": true
    },
    {
      "code": "CUTOVER_LEVEL",
      "name": "割接等级",
      "dataType": "STRING",
      "valueSource": "DICT:pms_risk_level",
      "owner": "CUT",
      "contextPath": "assessment.level",
      "enabled": true
    }
  ],
  "planTemplateSections": [
    {
      "stableSectionKey": "PRE_OPERATION",
      "title": "割接前准备",
      "sortOrder": 10,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C"
      ],
      "required": true
    },
    {
      "stableSectionKey": "OPERATION",
      "title": "操作步骤",
      "sortOrder": 20,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C",
        "D"
      ],
      "required": true
    },
    {
      "stableSectionKey": "CLOSING_COLLECTION",
      "title": "收尾采集",
      "sortOrder": 30,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C"
      ],
      "required": true
    },
    {
      "stableSectionKey": "POST_BUSINESS_TEST",
      "title": "割接后业务测试",
      "sortOrder": 40,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C"
      ],
      "required": true
    },
    {
      "stableSectionKey": "ROLLBACK",
      "title": "回退步骤",
      "sortOrder": 50,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C",
        "D"
      ],
      "required": true
    },
    {
      "stableSectionKey": "POST_CUTOVER_SUPPORT",
      "title": "割接后保障",
      "sortOrder": 60,
      "cutoverTypeCodes": [
        "DEVICE_REPLACE_WHOLE",
        "DEVICE_REPLACE_BOARD",
        "DEVICE_REPLACE_VENDOR",
        "DEVICE_ONBOARD",
        "VERSION_UPGRADE",
        "DISASTER_RECOVERY_DRILL",
        "CONFIGURATION_CHANGE",
        "NETWORK_TOPOLOGY_CHANGE",
        "VERSION_PATCH",
        "SIGNATURE_UPGRADE"
      ],
      "levelCodes": [
        "A",
        "B",
        "C"
      ],
      "required": true
    }
  ],
  "navigationRule": {
    "target": "CURRENT_STAGE_WORKBENCH"
  },
  "items": [
    {
      "stableItemKey": "SURVEY_CUTOVER_BACKGROUND",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "CUTOVER_BACKGROUND",
      "itemName": "割接背景",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "solvesOnlineIssue",
            "label": "是否解决网上问题",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "issueTicketNo",
            "label": "问题工单号",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "solvesOnlineIssue",
              "equals": true
            }
          },
          {
            "code": "issueHandler",
            "label": "工单处理人",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "solvesOnlineIssue",
              "equals": true
            }
          },
          {
            "code": "repeatCutover",
            "label": "本次是否为二次及以上割接",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "firstCutoverOwner",
            "label": "首次割接保障人",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "repeatCutover",
              "equals": true
            }
          },
          {
            "code": "backgroundDescription",
            "label": "割接背景表述",
            "type": "TEXT",
            "required": true
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 10
    },
    {
      "stableItemKey": "SURVEY_BUSINESS_SUMMARY",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "BUSINESS_SUMMARY",
      "itemName": "涉及业务简述",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "maxLength": 4000
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 20
    },
    {
      "stableItemKey": "SURVEY_IMPACT_SCOPE",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "IMPACT_SCOPE",
      "itemName": "割接动作影响范围",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "SELECT",
      "interfaceSchema": {
        "options": [
          {
            "value": "全部业务",
            "label": "全部业务"
          },
          {
            "value": "核心业务",
            "label": "核心业务"
          },
          {
            "value": "部分核心业务",
            "label": "部分核心业务"
          },
          {
            "value": "普通业务",
            "label": "普通业务"
          },
          {
            "value": "无业务",
            "label": "无业务"
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 30
    },
    {
      "stableItemKey": "SURVEY_CONTINUITY_REQUIREMENT",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "CONTINUITY_REQUIREMENT",
      "itemName": "业务连续性要求",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "SELECT",
      "interfaceSchema": {
        "options": [
          {
            "value": "不允许中断",
            "label": "不允许中断"
          },
          {
            "value": "闪断（1s内）",
            "label": "闪断（1s内）"
          },
          {
            "value": "短暂中断（1s-300s）",
            "label": "短暂中断（1s-300s）"
          },
          {
            "value": "长时间中断（300s-1800s）",
            "label": "长时间中断（300s-1800s）"
          },
          {
            "value": "没要求（>1800s）",
            "label": "没要求（>1800s）"
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 40
    },
    {
      "stableItemKey": "SURVEY_INTERRUPTION_COUNT",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "INTERRUPTION_COUNT",
      "itemName": "业务中断次数",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "type": "NUMBER"
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 50
    },
    {
      "stableItemKey": "SURVEY_CURRENT_TOPOLOGY",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "CURRENT_TOPOLOGY",
      "itemName": "现网拓扑图",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 60
    },
    {
      "stableItemKey": "SURVEY_DEVICE_LOCATION_PLAN",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "DEVICE_LOCATION_PLAN",
      "itemName": "设备部署位置规划",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "columns": [
          {
            "code": "deviceType",
            "label": "设备类型",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "serialNumber",
            "label": "设备序列号",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "deviceName",
            "label": "设备名称",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "location",
            "label": "设备安装位置",
            "type": "TEXT",
            "required": true
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 70
    },
    {
      "stableItemKey": "SURVEY_INTERFACE_INTERCONNECT_PLAN",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "INTERFACE_INTERCONNECT_PLAN",
      "itemName": "设备接口互联规划",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "columns": [
          {
            "code": "localDevice",
            "label": "本端设备",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "localInterface",
            "label": "本端接口",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "remoteInterface",
            "label": "对端接口",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "interfaceType",
            "label": "接口类型",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "cableModel",
            "label": "线缆或光模块型号",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "remark",
            "label": "备注",
            "type": "TEXT",
            "required": false
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 80
    },
    {
      "stableItemKey": "SURVEY_IP_VLAN_PLAN",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "IP_VLAN_PLAN",
      "itemName": "设备IP地址与VLAN规划",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "columns": [
          {
            "code": "deviceName",
            "label": "设备名称",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "interface",
            "label": "接口",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "ip",
            "label": "IP地址",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "mask",
            "label": "掩码",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "vrrpIp",
            "label": "VRRP地址（若有）",
            "type": "TEXT",
            "required": false
          },
          {
            "code": "gateway",
            "label": "网关",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "remark",
            "label": "备注",
            "type": "TEXT",
            "required": false
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 90
    },
    {
      "stableItemKey": "SURVEY_PERFORMANCE_BASELINE",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "PERFORMANCE_BASELINE",
      "itemName": "设备性能基线",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "columns": [
          {
            "code": "serialNumber",
            "label": "设备序列号",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "concurrentConnections",
            "label": "并发连接数",
            "type": "NUMBER",
            "required": true
          },
          {
            "code": "newConnections",
            "label": "新建连接数",
            "type": "NUMBER",
            "required": true
          },
          {
            "code": "cpuUsage",
            "label": "CPU使用率",
            "type": "NUMBER",
            "required": true
          },
          {
            "code": "memoryUsage",
            "label": "内存使用率",
            "type": "NUMBER",
            "required": true
          }
        ]
      },
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 100
    },
    {
      "stableItemKey": "SURVEY_CONNECTIVITY_TEST_CASE",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "CONNECTIVITY_TEST_CASE",
      "itemName": "业务连通性测试例核验",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 110
    },
    {
      "stableItemKey": "SURVEY_VENDOR_CONFIG_TRANSLATION",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "VENDOR_CONFIG_TRANSLATION",
      "itemName": "友商配置全量翻译检查",
      "itemDescription": "基线 CUT-10；字段补充来源：0807设计表 3.调研及风险考察、3.1割接背景。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "STRUCTURED",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 120
    },
    {
      "stableItemKey": "RISK_CURRENT_VERSION_BULLETIN",
      "itemType": "RISK",
      "businessCategoryCode": "CURRENT_VERSION_BULLETIN",
      "itemName": "技术公告检查项－当前版本",
      "itemDescription": "基线 CUT-09 风险考察项。外部技术公告契约接通前使用人工查询资料，不伪造公告接口返回。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "hasBulletins",
            "label": "是否存在相关技术公告",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "queryBasis",
            "label": "查询版本、资料来源及查询时间",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "bulletins",
            "label": "技术公告清单",
            "type": "TABLE",
            "required": true,
            "columns": [
              {
                "code": "number",
                "label": "编号",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "subject",
                "label": "主题",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "description",
                "label": "描述",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "solution",
                "label": "解决方案",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "involved",
                "label": "本次是否涉及",
                "type": "BOOLEAN",
                "required": true
              },
              {
                "code": "measures",
                "label": "采取措施",
                "type": "TEXT",
                "required": true,
                "visibleWhen": {
                  "field": "involved",
                  "equals": true
                }
              }
            ],
            "visibleWhen": {
              "field": "hasBulletins",
              "equals": true
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 130
    },
    {
      "stableItemKey": "RISK_TARGET_VERSION_BULLETIN",
      "itemType": "RISK",
      "businessCategoryCode": "TARGET_VERSION_BULLETIN",
      "itemName": "技术公告检查项－升级后版本",
      "itemDescription": "基线 CUT-09 风险考察项。外部技术公告契约接通前使用人工查询资料，不伪造公告接口返回。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "hasBulletins",
            "label": "是否存在相关技术公告",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "queryBasis",
            "label": "查询版本、资料来源及查询时间",
            "type": "TEXT",
            "required": true
          },
          {
            "code": "bulletins",
            "label": "技术公告清单",
            "type": "TABLE",
            "required": true,
            "columns": [
              {
                "code": "number",
                "label": "编号",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "subject",
                "label": "主题",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "description",
                "label": "描述",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "solution",
                "label": "解决方案",
                "type": "TEXT",
                "required": true
              },
              {
                "code": "involved",
                "label": "本次是否涉及",
                "type": "BOOLEAN",
                "required": true
              },
              {
                "code": "measures",
                "label": "采取措施",
                "type": "TEXT",
                "required": true,
                "visibleWhen": {
                  "field": "involved",
                  "equals": true
                }
              }
            ],
            "visibleWhen": {
              "field": "hasBulletins",
              "equals": true
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 140
    },
    {
      "stableItemKey": "RISK_DUAL_CONFIG_CONSISTENCY",
      "itemType": "RISK",
      "businessCategoryCode": "DUAL_CONFIG_CONSISTENCY",
      "itemName": "双机必要配置一致性检查",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 150
    },
    {
      "stableItemKey": "RISK_FILTER_NAT_QOS_COMPILE_COUNT",
      "itemType": "RISK",
      "businessCategoryCode": "FILTER_NAT_QOS_COMPILE_COUNT",
      "itemName": "包过滤/NAT/QOS编译数检查",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 160
    },
    {
      "stableItemKey": "RISK_COMPILE_LIMIT_ASSESSMENT",
      "itemType": "RISK",
      "businessCategoryCode": "COMPILE_LIMIT_ASSESSMENT",
      "itemName": "编译数超限评估",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 170
    },
    {
      "stableItemKey": "RISK_SESSION_SYNC",
      "itemType": "RISK",
      "businessCategoryCode": "SESSION_SYNC",
      "itemName": "会话同步是否开启",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 180
    },
    {
      "stableItemKey": "RISK_DUAL_CONTROLLER_VERSION",
      "itemType": "RISK",
      "businessCategoryCode": "DUAL_CONTROLLER_VERSION",
      "itemName": "框式设备双主控版本一致性",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 190
    },
    {
      "stableItemKey": "RISK_PACKAGE_MD5",
      "itemType": "RISK",
      "businessCategoryCode": "PACKAGE_MD5",
      "itemName": "升级包/补丁包MD5值校验",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 200
    },
    {
      "stableItemKey": "RISK_MAJOR_PROJECT_SPARES",
      "itemType": "RISK",
      "businessCategoryCode": "MAJOR_PROJECT_SPARES",
      "itemName": "重大项目备件申请",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "majorCutover",
            "label": "本次是否为重大割接项目",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "spareReference",
            "label": "备件外部申请引用或人工核验说明",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "majorCutover",
              "equals": true
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 210
    },
    {
      "stableItemKey": "RISK_SYSTEM_LOG",
      "itemType": "RISK",
      "businessCategoryCode": "SYSTEM_LOG",
      "itemName": "系统日志告警检查",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "COLLECTION",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 220
    },
    {
      "stableItemKey": "RISK_DIAGNOSTIC_LOG",
      "itemType": "RISK",
      "businessCategoryCode": "DIAGNOSTIC_LOG",
      "itemName": "诊断日志告警检查",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "COLLECTION",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 230
    },
    {
      "stableItemKey": "RISK_RUNNING_VERSION_BACKUP",
      "itemType": "RISK",
      "businessCategoryCode": "RUNNING_VERSION_BACKUP",
      "itemName": "运行版本备份",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 240
    },
    {
      "stableItemKey": "RISK_HOT_PATCH_BACKUP",
      "itemType": "RISK",
      "businessCategoryCode": "HOT_PATCH_BACKUP",
      "itemName": "热补丁备份",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 250
    },
    {
      "stableItemKey": "RISK_LICENSE_BACKUP",
      "itemType": "RISK",
      "businessCategoryCode": "LICENSE_BACKUP",
      "itemName": "License授权文件备份",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 260
    },
    {
      "stableItemKey": "RISK_CONFIG_BACKUP",
      "itemType": "RISK",
      "businessCategoryCode": "CONFIG_BACKUP",
      "itemName": "配置文件备份",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 270
    },
    {
      "stableItemKey": "RISK_DYNAMIC_TABLE_COLLECTION",
      "itemType": "RISK",
      "businessCategoryCode": "DYNAMIC_TABLE_COLLECTION",
      "itemName": "设备动态表项数据采集",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "COLLECTION",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 280
    },
    {
      "stableItemKey": "RISK_MTU_JUMBO_FRAME",
      "itemType": "RISK",
      "businessCategoryCode": "MTU_JUMBO_FRAME",
      "itemName": "MTU和超大帧评估",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 290
    },
    {
      "stableItemKey": "RISK_HUNDRED_G_FEC",
      "itemType": "RISK",
      "businessCategoryCode": "HUNDRED_G_FEC",
      "itemName": "百G口FEC开启确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 300
    },
    {
      "stableItemKey": "RISK_LONG_CONNECTION",
      "itemType": "RISK",
      "businessCategoryCode": "LONG_CONNECTION",
      "itemName": "长连接业务确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 310
    },
    {
      "stableItemKey": "RISK_SECOND_PASS_DEVICE",
      "itemType": "RISK",
      "businessCategoryCode": "SECOND_PASS_DEVICE",
      "itemName": "流量二次过设备确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 320
    },
    {
      "stableItemKey": "RISK_STP",
      "itemType": "RISK",
      "businessCategoryCode": "STP",
      "itemName": "STP生成树开启确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 330
    },
    {
      "stableItemKey": "RISK_F5_DEFAULT",
      "itemType": "RISK",
      "businessCategoryCode": "F5_DEFAULT",
      "itemName": "F5缺省参数确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 340
    },
    {
      "stableItemKey": "RISK_ADWARE_DEFAULT",
      "itemType": "RISK",
      "businessCategoryCode": "ADWARE_DEFAULT",
      "itemName": "Adware缺省参数确认",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "normal",
            "label": "检查是否正常",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ]
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 350
    },
    {
      "stableItemKey": "RISK_ROOM_OPERATION_COMMITMENT",
      "itemType": "RISK",
      "businessCategoryCode": "ROOM_OPERATION_COMMITMENT",
      "itemName": "机房操作承诺书",
      "itemDescription": "基线 CUT-09 风险考察项。",
      "interfaceFormat": "FILE",
      "interfaceSchema": {},
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "MANUAL",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 360
    },
    {
      "stableItemKey": "DUAL_VSM_R003_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否为多板级联",
      "itemDescription": "判断是否存在级联通道单点故障风险\\n来源：0807设计表，3.2双机部署规范性检查表，第3行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 3,
          "originalName": "是否为多板级联",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 370
    },
    {
      "stableItemKey": "DUAL_VSM_R004_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "单级联板环境是否配置多个级联口",
      "itemDescription": "判断是否存在级联通道单点故障风险\\n来源：0807设计表，3.2双机部署规范性检查表，第4行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 4,
          "originalName": "单级联板环境是否配置多个级联口",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 380
    },
    {
      "stableItemKey": "DUAL_VSM_R005_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "单台成员设备上的级联板型号是否一致",
      "itemDescription": "不支持不同型号板卡做跨槽级联\\n来源：0807设计表，3.2双机部署规范性检查表，第5行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 5,
          "originalName": "单台成员设备上的级联板型号是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 390
    },
    {
      "stableItemKey": "DUAL_VSM_R006_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "两台成员设备上级联板型号是否一致",
      "itemDescription": "不支持不同型号板卡做级联\\n来源：0807设计表，3.2双机部署规范性检查表，第6行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 6,
          "originalName": "两台成员设备上级联板型号是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 400
    },
    {
      "stableItemKey": "DUAL_VSM_R007_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "两台成员设备上级联板所在槽位号是否一致",
      "itemDescription": "两台成员设备上级联板卡槽位必须一致\\n来源：0807设计表，3.2双机部署规范性检查表，第7行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 7,
          "originalName": "两台成员设备上级联板所在槽位号是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 410
    },
    {
      "stableItemKey": "DUAL_VSM_R008_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "两台成员设备上级联口接口号是否一致",
      "itemDescription": "两台成员设备上级联口接口号必须一致\\n来源：0807设计表，3.2双机部署规范性检查表，第8行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 8,
          "originalName": "两台成员设备上级联口接口号是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 420
    },
    {
      "stableItemKey": "DUAL_VSM_R009_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "两台成员设备级联线是否通过中间设备",
      "itemDescription": "传输网络中设备必须支持所有格式报文进行透传\\n来源：0807设计表，3.2双机部署规范性检查表，第9行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 9,
          "originalName": "两台成员设备级联线是否通过中间设备",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 430
    },
    {
      "stableItemKey": "DUAL_VSM_R010_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "两台成员设备上级联口是否按照接口顺序接线，例如：tengige0_3_0对接tengige1_3_0",
      "itemDescription": "级联口必须按接口顺序接线\\n来源：0807设计表，3.2双机部署规范性检查表，第10行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 10,
          "originalName": "两台成员设备上级联口是否按照接口顺序接线，例如：tengige0_3_0对接tengige1_3_0",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 440
    },
    {
      "stableItemKey": "DUAL_VSM_R011_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "级联口接口速率是否为默认值",
      "itemDescription": "级联口速率禁止修改\\n来源：0807设计表，3.2双机部署规范性检查表，第11行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 11,
          "originalName": "级联口接口速率是否为默认值",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 450
    },
    {
      "stableItemKey": "DUAL_VSM_R012_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "级联口接口类型是否一致",
      "itemDescription": "不允许光电口混用，不允许不同速率的接口混用\\n来源：0807设计表，3.2双机部署规范性检查表，第12行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 12,
          "originalName": "级联口接口类型、速率是否一致",
          "part": 1,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 460
    },
    {
      "stableItemKey": "DUAL_VSM_R012_2",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "级联口接口速率是否一致",
      "itemDescription": "不允许光电口混用，不允许不同速率的接口混用\\n来源：0807设计表，3.2双机部署规范性检查表，第12行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 12,
          "originalName": "级联口接口类型、速率是否一致",
          "part": 2,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 470
    },
    {
      "stableItemKey": "DUAL_VSM_R013_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "级联口带宽是否大于等于现网设备整机承载吞吐的80%",
      "itemDescription": "流定义环境存在级联通道带宽不足风险\\n来源：0807设计表，3.2双机部署规范性检查表，第13行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 13,
          "originalName": "级联口带宽是否大于等于现网设备整机承载吞吐的80%",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 480
    },
    {
      "stableItemKey": "DUAL_VSM_R014_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "分裂检测功能是否开启",
      "itemDescription": "VSM分裂后存在双主风险\\n来源：0807设计表，3.2双机部署规范性检查表，第14行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 14,
          "originalName": "分裂检测功能是否开启",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 490
    },
    {
      "stableItemKey": "DUAL_VSM_R015_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "双主控分裂检测的四个主控管理口是否连接到同一交换机",
      "itemDescription": "如果四个主控的HA口/管理口没有连接到同一交换机且二层可通则设备发生双主控切换后会导致分裂检测功能失效\\n来源：0807设计表，3.2双机部署规范性检查表，第15行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 15,
          "originalName": "双主控环境下选择主控HA口/管理口作为分裂检测口时是否将四个主控的管理口连接到了同一个交换机上且二层可通",
          "part": 1,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 500
    },
    {
      "stableItemKey": "DUAL_VSM_R015_2",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "双主控分裂检测的四个主控管理口是否二层可通",
      "itemDescription": "如果四个主控的HA口/管理口没有连接到同一交换机且二层可通则设备发生双主控切换后会导致分裂检测功能失效\\n来源：0807设计表，3.2双机部署规范性检查表，第15行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 15,
          "originalName": "双主控环境下选择主控HA口/管理口作为分裂检测口时是否将四个主控的管理口连接到了同一个交换机上且二层可通",
          "part": 2,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 510
    },
    {
      "stableItemKey": "DUAL_VSM_R016_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "当选取面板口作为分裂检测口时，选取的分裂检测口是否为非级联板上的接口",
      "itemDescription": "当级联板发生故障时分裂检测功能失效VSM 状态切为双主\\n来源：0807设计表，3.2双机部署规范性检查表，第16行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 16,
          "originalName": "当选取面板口作为分裂检测口时，选取的分裂检测口是否为非级联板上的接口",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 520
    },
    {
      "stableItemKey": "DUAL_VSM_R017_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "VSM环境的上下行业务口是否对称组网(跨框聚合或跨框等价路由)",
      "itemDescription": "单成员设备或单链路故障会导致业务中断\\n来源：0807设计表，3.2双机部署规范性检查表，第17行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 17,
          "originalName": "VSM环境的上下行业务口是否对称组网(跨框聚合或跨框等价路由)",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "VSM",
      "enabled": true,
      "sortOrder": 530
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R025_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口接口配置，三层物理口还是access口",
      "itemDescription": "心跳口若是二层口，则禁止使用trunk模式，必须是access模式\\n来源：0807设计表，3.2双机部署规范性检查表，第25行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 25,
          "originalName": "心跳口接口配置，三层物理口还是access口",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 540
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R026_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳链路之间是否存在网络设备",
      "itemDescription": "心跳口优选直连，非直连请提前咨询总部确认\\n来源：0807设计表，3.2双机部署规范性检查表，第26行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 26,
          "originalName": "心跳链路之间是否存在网络设备",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 550
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R027_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口光模块收发光检查",
      "itemDescription": "若存在成员口光模块收发光异常，需要及时更换光模块\\n来源：0807设计表，3.2双机部署规范性检查表，第27行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 27,
          "originalName": "心跳口光模块收发光检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 560
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R028_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口接口计数统计检查",
      "itemDescription": "dev_cfg eth sw show all查看，如果存在crc丢包或者其他丢包持续增长，则需要进一步定位\\n来源：0807设计表，3.2双机部署规范性检查表，第28行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 28,
          "originalName": "心跳口接口计数统计检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 570
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R029_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳接口ip是否为私网ip",
      "itemDescription": "心跳接口ip一定要配置私网ip（10.0.0.0/8，172.16.0.0/16，192.168.0.0/24，且不允许与现网地址冲突），尽量不要配置公网ip，否则在开启地域访问控制策略场景可能会影响会话及配置同步功能。\\n来源：0807设计表，3.2双机部署规范性检查表，第29行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 29,
          "originalName": "心跳接口ip是否为私网ip",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 580
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R030_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机心跳口是否配置接口服务限制",
      "itemDescription": "心跳口禁止配置接口服务限制\\n来源：0807设计表，3.2双机部署规范性检查表，第30行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 30,
          "originalName": "主备机心跳口是否配置接口服务限制",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 590
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R031_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口互ping是否通",
      "itemDescription": "双机状态维护为组播报文，配置同步为tcp报文，如果双机心跳口ping不通，则可能存在配置同步功能不好使的情况\\n来源：0807设计表，3.2双机部署规范性检查表，第31行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 31,
          "originalName": "心跳口互ping是否通",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 600
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R032_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口聚合口配置是否一致",
      "itemDescription": "心跳接口必须为静态链路聚合模式，如果需要使用动态链路聚合必须联系总部评审决策\\n来源：0807设计表，3.2双机部署规范性检查表，第32行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 32,
          "originalName": "心跳口聚合口配置是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 610
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R033_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "静默双机状态是否符合预期",
      "itemDescription": "双机状态是否符合预期，状态维持时间是否符合预期\\n来源：0807设计表，3.2双机部署规范性检查表，第33行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 33,
          "originalName": "静默双机状态检查",
          "part": 1,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 620
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R033_2",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "静默双机状态维持时间是否符合预期",
      "itemDescription": "双机状态是否符合预期，状态维持时间是否符合预期\\n来源：0807设计表，3.2双机部署规范性检查表，第33行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 33,
          "originalName": "静默双机状态检查",
          "part": 2,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 630
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R034_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否所有业务接口都配置静默接口（不包含管理口、心跳口、配置同步接口）",
      "itemDescription": "1、备机需要将静默接口列表中的接口静默，否则会出现IP地址冲突以及流量继续转发至备机的情况，特别是新扩容的接口，容易出现遗漏的情况。\\n2、静默接口中不能配置管理口、心跳口以及配置同步接口，否则会导致备机无法管理、双机状态异常、配置同步异常等情况\\n来源：0807设计表，3.2双机部署规范性检查表，第34行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 34,
          "originalName": "是否所有业务接口都配置静默接口（不包含管理口、心跳口、配置同步接口）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 640
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R035_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否所有业务接口都配置监听接口（包含聚合接口）",
      "itemDescription": "要求所有业务接口都配置监听接口，包含bond口，防止bond口down物理口up情况下双机无法切换而影响业务\\n来源：0807设计表，3.2双机部署规范性检查表，第35行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 35,
          "originalName": "是否所有业务接口都配置监听接口（包含聚合接口）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 650
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R036_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "备机静默接口是否处于slient静默状态",
      "itemDescription": "开发者试图执行 dev_cfg mac 接口 st 命令查看接口静默状态为slient\\n来源：0807设计表，3.2双机部署规范性检查表，第36行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 36,
          "originalName": "备机静默接口是否处于slient静默状态",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 660
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R037_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机管理协议http/https/telnet/ssh开启状态是否一致",
      "itemDescription": "配置同步使用的是http/https/telnet/ssh协议，必须保证静默双机两端设备http/https/telnet/ssh配置一致\\n来源：0807设计表，3.2双机部署规范性检查表，第37行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 37,
          "originalName": "主备机管理协议http/https/telnet/ssh开启状态是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 670
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R038_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "配置同步功能检查",
      "itemDescription": "检查主备配置同步禁用开关是否关闭，默认开关关闭，此情况下配置同步才可正常。（功能位于 高可靠性--双机热备--配置同步参数 模块高级配置）\\n来源：0807设计表，3.2双机部署规范性检查表，第38行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 38,
          "originalName": "配置同步功能检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 680
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R039_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机会话同步功能是否均开启",
      "itemDescription": "主备机会话同步配置必须一致。若会话同步未开启，则主备切换会导致部分长连接业务持续异常，此情况下需让客户手动重新拉起业务。\\n来源：0807设计表，3.2双机部署规范性检查表，第39行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 39,
          "originalName": "主备机会话同步功能是否均开启",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 690
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R040_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否开启双机回切延时（ hotbackup shrp delay time xxx）",
      "itemDescription": "主备回切若配置延时，则回切会按照配置的时间延时后再回切，在部分场景下不能配置该功能，例如割接过程中出现问题快速回退，如果配置了该功能，就无法迅速回切来达到恢复业务的目的（可以通过直接修改初始优先级来实现主备迅速回切）备注：神七D035开局版本及之后版本都支持\\n来源：0807设计表，3.2双机部署规范性检查表，第40行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 40,
          "originalName": "是否开启双机回切延时（ hotbackup shrp delay time xxx）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 700
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R041_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置接口延迟up",
      "itemDescription": "主要用于框式设备，以防备机在重启过程中因业务板未重启完成的情况接口短暂up影响业务。页面高可靠性--接口延迟配置查看是否有配置，或者命令行show run | in linkup-delay查看是否存在接口延迟up配置，然后再确定是哪些接口有此配置\\n来源：0807设计表，3.2双机部署规范性检查表，第41行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 41,
          "originalName": "是否有配置接口延迟up",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 710
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R042_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置静默双机监听业务板cpu（hotbackup shrp monitor-board slot x priority n）",
      "itemDescription": "主要用于框式设备，防止在单业务板场景下，主设备业务板重启，主备状态未切换而影响业务。备注：神七D035开局版本及之后版本都支持\\n来源：0807设计表，3.2双机部署规范性检查表，第42行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 42,
          "originalName": "是否有配置静默双机监听业务板cpu（hotbackup shrp monitor-board slot x priority n）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 720
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R043_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查是否需要配置Track ip",
      "itemDescription": "设备静默双机环境，上下行对接二层交换机或透明部署设备，仅通过接口 UP/DOWN 状态无法判断业务实际连通性，需配置 Track IP。Track ip联动静默双机必须在双机初始优先级一样的场景下使用，优先级不一致会导致Track ip联动切换失效。\\nFW 整机纯透明、上下行直连三层设备：链路断裂、对端设备断电、接口Down会直接造成防火墙接口 Down，依靠静默双机接口监控就能正常切主备，无需配置 Track IP。\\n备注：神六D198官网及之后版本支持\\n神七所有版本均支持\\n来源：0807设计表，3.2双机部署规范性检查表，第43行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 43,
          "originalName": "检查是否需要配置Track ip",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 730
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R044_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查IP是否可以配置为Track ip",
      "itemDescription": "探测 IP 需保证主、备两台防火墙路由均可正常可达、网络稳定性高，优先沿着业务转发路径选取。1）首选：对端三层互联的 IP地址；2）备选：其他内网核心网关/固定业务服务器IP。\\nTrack IP 禁止选用：1）仅主防火墙可达、备防火墙路由不通的IP；2）频繁离线、在线状态波动较大的终端IP。\\n备注：神六D198官网及之后版本支持\\n神七所有版本均支持\\n来源：0807设计表，3.2双机部署规范性检查表，第44行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 44,
          "originalName": "检查IP是否可以配置为Track ip",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 740
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R045_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否配置有trackip\\n（trackip name xxxx icmp track-address x.x.x.x output-interface  vlan-if20(出接口) next-hop x.x.x.x src-ip x.x.x.x）",
      "itemDescription": "用于链路健康检查，对链路状态进行监听。此配置会双机同步，output-interface（出接口）、next-hop（下一条）属于可选参数，可不配置。\\n备注：神六D198官网及之后版本支持\\n神七所有版本均支持\\n来源：0807设计表，3.2双机部署规范性检查表，第45行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 45,
          "originalName": "是否配置有trackip\\n（trackip name xxxx icmp track-address x.x.x.x output-interface  vlan-if20(出接口) next-hop x.x.x.x src-ip x.x.x.x）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 750
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R046_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置静默双机监听trackip\\n（hotbackup shrp trackip xxxx）",
      "itemDescription": "引用链路健康检查，达到在主机接口状态UP，但是网络不通的情况下进行双机切换，快速恢复业务的目的，此配置不能同步，每一台设备都需要手动配置调用Track ip策略。\\n备注：神六D198官网及之后版本支持\\n神七所有版本均支持\\n来源：0807设计表，3.2双机部署规范性检查表，第46行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 46,
          "originalName": "是否有配置静默双机监听trackip\\n（hotbackup shrp trackip xxxx）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 760
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R047_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查上下游设备是否有配置静态ARP/ND、MAC/IP绑定",
      "itemDescription": "如果上下游设备配置静态ARP/ND或MAC/IP绑定功能，主备机切换后会导致业务不通（部分组网中会开启主备机接口的MAC地址同步，同样也需要检查上下游设备静态ARP/ND或MAC/IP绑定是否有绑定接口）\\n来源：0807设计表，3.2双机部署规范性检查表，第47行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 47,
          "originalName": "检查上下游设备是否有配置静态ARP/ND、MAC/IP绑定",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 770
    },
    {
      "stableItemKey": "DUAL_SILENT_DUAL_R048_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查上下游设备是否有开启学习免费ARP",
      "itemDescription": "部分厂商默认不开启免费ARP学习或者关闭了免费ARP学习功能，会导致备机切为主机后需要等ARP老化后重新学习流量才能通，影响双机的收敛时间\\n来源：0807设计表，3.2双机部署规范性检查表，第48行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 48,
          "originalName": "检查上下游设备是否有开启学习免费ARP",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "SILENT_DUAL",
      "enabled": true,
      "sortOrder": 780
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R056_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口之间是否存在网络设备",
      "itemDescription": "心跳口优选直连，非直连请提前咨询总部确认\\n来源：0807设计表，3.2双机部署规范性检查表，第56行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 56,
          "originalName": "DRP心跳口之间是否存在网络设备",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 790
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R057_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口光模块收发光检查",
      "itemDescription": "若存在成员口光模块收发光异常，需要及时更换光模块\\n来源：0807设计表，3.2双机部署规范性检查表，第57行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 57,
          "originalName": "DRP心跳口光模块收发光检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 800
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R058_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口接口计数统计检查",
      "itemDescription": "命令：dev_cfg eth sw show all查看，对应心跳物理口如果存在crc丢包、rxdrop丢包或txdrop丢包持续增长。错包多会影响心跳状态及同步配置情况。\\n来源：0807设计表，3.2双机部署规范性检查表，第58行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 58,
          "originalName": "DRP心跳口接口计数统计检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 810
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R059_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口ip是否为私网ip",
      "itemDescription": "心跳接口ip一定要配置私网ip（10.0.0.0/8，172.16.0.0/16，192.168.0.0/24，且不允许与现网地址冲突），尽量不要配置公网ip，否则在开启地域访问控制策略场景可能会影响会话及配置同步功能。\\n来源：0807设计表，3.2双机部署规范性检查表，第59行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 59,
          "originalName": "DRP心跳口ip是否为私网ip",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 820
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R060_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口作为配置同步接口时是否配置接口服务限制",
      "itemDescription": "心跳口作为配置同步接口时禁止配置接口服务限制\\n来源：0807设计表，3.2双机部署规范性检查表，第60行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 60,
          "originalName": "DRP心跳口作为配置同步接口时是否配置接口服务限制",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 830
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R061_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口互ping是否通",
      "itemDescription": "1：DRP状态维护为组播报文，如果DRP心跳口ping不通，则DRP状态异常。2：如果DRP心跳口ping不通，当心跳口作为配置同步接口时会存在配置同步异常情况。\\n来源：0807设计表，3.2双机部署规范性检查表，第61行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 61,
          "originalName": "DRP心跳口互ping是否通",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 840
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R062_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP心跳口聚合口配置是否一致",
      "itemDescription": "优先静态聚合，检查心跳口bond聚合本端和对端是否一致\\n来源：0807设计表，3.2双机部署规范性检查表，第62行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 62,
          "originalName": "DRP心跳口聚合口配置是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 850
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R063_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP主备状态检查",
      "itemDescription": "双机状态是否符合预期。\\n来源：0807设计表，3.2双机部署规范性检查表，第63行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 63,
          "originalName": "DRP主备状态检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 860
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R064_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "双机动态路由cost值是否正常",
      "itemDescription": "1：查看设备发布出去的ospf路由cost值是否符合设备状态，通过show ip ospf database self-originate 命令查看本设备adv-router,下一步通过命令show ip ospf （进程号）database external adv-router XXX.XXX.XXX.XXX（本设备路由器id值）  查看该adv-router的 metric值来判断是否符合预期。\\n2:查看设备发布出去的bgp路由cost值是否符合设备状态，通过show ip bgp ipv4 unicast 查看metric值来判断是否符合预期。\\n来源：0807设计表，3.2双机部署规范性检查表，第64行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 64,
          "originalName": "双机动态路由cost值是否正常",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 870
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R065_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "配置同步功能检查",
      "itemDescription": "检查主备配置同步禁用开关是否关闭，默认开关关闭，此情况下配置同步才可正常。（功能位于 高可靠性--普通双机-禁用所有配置同步模块）\\n来源：0807设计表，3.2双机部署规范性检查表，第65行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 65,
          "originalName": "配置同步功能检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 880
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R066_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否开启drp抢占延迟，drp preempt delay X",
      "itemDescription": "drp延迟抢占配置180S，应用场景：当主设备（fw1）重启后，备（fw2）接替了主，在旧主（fw1）重启完成后保证fw1的所有表项准备完成后再接替主的状态，随后再引流量到设备处理。\\n来源：0807设计表，3.2双机部署规范性检查表，第66行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 66,
          "originalName": "是否开启drp抢占延迟，drp preempt delay X",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 890
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R067_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "interface 接口（tengigeX_X） linkup-delay 180",
      "itemDescription": "业务接口延迟UP配置180S，所有业务口都需要配置，保证设备重启时OSPF进程先于DRP进程启动后，OSPF先切为主。\\n来源：0807设计表，3.2双机部署规范性检查表，第67行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 67,
          "originalName": "interface 接口（tengigeX_X） linkup-delay 180",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 900
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R068_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置drp监听业务板drp track board slotX reduced 10",
      "itemDescription": "主要用于框式设备，防止在单业务板场景下，主设备业务板重启，主备状态未切换而影响业务。业务板卡要监听所有的。每个业务板推荐值是10，按实际情况调整。假设该设备业务板坏了，导致设备状态切换，此时业务流量将不会走这台设备。\\n来源：0807设计表，3.2双机部署规范性检查表，第68行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 68,
          "originalName": "是否有配置drp监听业务板drp track board slotX reduced 10",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 910
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R069_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置监听接口drp track interface 接口 reduced 10",
      "itemDescription": "用于所有设备，防止在接口出现异常时未及时切换状态，导致影响业务。通常配置：监听上下行所有业务接口。推荐配置每个接口优先级为10,假设一台设备接口down了，导致设备状态切换到备的话，此时业务流量不会走这台设备。\\n来源：0807设计表，3.2双机部署规范性检查表，第69行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 69,
          "originalName": "是否有配置监听接口drp track interface 接口 reduced 10",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 920
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R070_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "drp虚拟ip配置一致性",
      "itemDescription": "drp虚拟ip配置保持一致。否则会出现主备协商异常的风险。\\n来源：0807设计表，3.2双机部署规范性检查表，第70行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 70,
          "originalName": "drp虚拟ip配置一致性",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 930
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R071_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP监听接口优先级是否符合设计",
      "itemDescription": "根据现网设计需求配置主备优先级差异，满足客户主备切换需求。监听接口(板卡)/监听IP/BFD，优先级默认值 10（1-255)。\\n来源：0807设计表，3.2双机部署规范性检查表，第71行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 71,
          "originalName": "优先级确认：确认优先级配置是否符合设计，监听接口/监听IP/BFD",
          "part": 1,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 940
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R071_2",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP监听IP优先级是否符合设计",
      "itemDescription": "根据现网设计需求配置主备优先级差异，满足客户主备切换需求。监听接口(板卡)/监听IP/BFD，优先级默认值 10（1-255)。\\n来源：0807设计表，3.2双机部署规范性检查表，第71行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 71,
          "originalName": "优先级确认：确认优先级配置是否符合设计，监听接口/监听IP/BFD",
          "part": 2,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 950
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R071_3",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP监听BFD优先级是否符合设计",
      "itemDescription": "根据现网设计需求配置主备优先级差异，满足客户主备切换需求。监听接口(板卡)/监听IP/BFD，优先级默认值 10（1-255)。\\n来源：0807设计表，3.2双机部署规范性检查表，第71行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 71,
          "originalName": "优先级确认：确认优先级配置是否符合设计，监听接口/监听IP/BFD",
          "part": 3,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 960
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R072_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP是否关联动态路由",
      "itemDescription": "1：命令show hotbackup ip ospf-cost-adjust 查看DRP关联ospf状态\\n2、命令 router bgp （ID）进入bgp视图，bgp视图下show this 查看hotbackup bgp-cost-adjust enable命令是否存在。\\n来源：0807设计表，3.2双机部署规范性检查表，第72行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 72,
          "originalName": "DRP是否关联动态路由",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 970
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R073_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP是否关联源nat",
      "itemDescription": "show run查看drp source-nat primary|secondary是否存在。\\n来源：0807设计表，3.2双机部署规范性检查表，第73行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 73,
          "originalName": "DRP是否关联源nat",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 980
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R074_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP是否开启会话同步",
      "itemDescription": "show session-sync configuration查看会话同步是否正常。\\n来源：0807设计表，3.2双机部署规范性检查表，第74行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 74,
          "originalName": "DRP是否开启会话同步",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 990
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R075_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP是否关联普通双机",
      "itemDescription": "show hotbackup state查看普通双机是否正常。\\n来源：0807设计表，3.2双机部署规范性检查表，第75行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 75,
          "originalName": "DRP是否关联普通双机",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 1000
    },
    {
      "stableItemKey": "DUAL_DRP_DUAL_R076_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "DRP统计信息检查",
      "itemDescription": "show drp statistics查看DRP报文统计信息。\\n来源：0807设计表，3.2双机部署规范性检查表，第76行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 76,
          "originalName": "DRP统计信息检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "DRP_DUAL",
      "enabled": true,
      "sortOrder": 1010
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R084_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "配置双机前设备是否清库",
      "itemDescription": "1、全新入网设备：组建双机前必须执行清库重启操作，彻底清除设备出厂残留配置，避免因残留配置影响后续业务。\\n2、在网设备扩容组建双机：先对现网运行设备完成双机基础配置，并导出完整整机配置文件；将配置文件导入新增备机后，针对性修改备机专属双机参数，再完成双机组网。\\n来源：0807设计表，3.2双机部署规范性检查表，第84行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 84,
          "originalName": "配置双机前设备是否清库",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1020
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R085_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "心跳口接口配置，三层物理口还是access口",
      "itemDescription": "心跳口若是二层口，则禁止使用trunk模式，必须是access模式，对于框式设备禁止使用三层物理口做心跳接口及同步接口\\n来源：0807设计表，3.2双机部署规范性检查表，第85行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 85,
          "originalName": "心跳口接口配置，三层物理口还是access口",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1030
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R086_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步链路之间是否存在网络设备",
      "itemDescription": "同步接口优选直连，非直连请提前咨询总部确认\\n来源：0807设计表，3.2双机部署规范性检查表，第86行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 86,
          "originalName": "同步链路之间是否存在网络设备",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1040
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R087_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步接口光模块收发光检查",
      "itemDescription": "若存在成员口光模块收发光异常，需要及时更换光模块\\n来源：0807设计表，3.2双机部署规范性检查表，第87行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 87,
          "originalName": "同步接口光模块收发光检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1050
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R088_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步接口计数统计检查",
      "itemDescription": "dev_cfg eth sw show all查看，对应心跳物理口如果存在crc丢包、rxdrop丢包或txdrop丢包持续增长。错包多会影响心跳状态及同步配置情况。\\n来源：0807设计表，3.2双机部署规范性检查表，第88行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 88,
          "originalName": "同步接口计数统计检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1060
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R089_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步接口ip是否为私网ip",
      "itemDescription": "同步接口ip一定要配置私网ip（10.0.0.0/8，172.16.0.0/16，192.168.0.0/24，且不允许与现网地址冲突），尽量不要配置公网ip，否则在开启地域访问控制策略场景可能会影响会话及配置同步功能。\\n来源：0807设计表，3.2双机部署规范性检查表，第89行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 89,
          "originalName": "同步接口ip是否为私网ip",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1070
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R090_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机同步接口是否配置接口服务限制",
      "itemDescription": "同步接口禁止配置接口服务限制\\n来源：0807设计表，3.2双机部署规范性检查表，第90行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 90,
          "originalName": "主备机同步接口是否配置接口服务限制",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1080
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R091_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步接口互ping是否通",
      "itemDescription": "双机状态维护为组播报文，配置同步为tcp报文，如果双机同步接口ping不通，则可能存在配置同步功能不好使的情况\\n来源：0807设计表，3.2双机部署规范性检查表，第91行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 91,
          "originalName": "同步接口互ping是否通",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1090
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R092_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "同步接口聚合口配置是否一致",
      "itemDescription": "优先静态聚合，检查同步接口bond聚合本端和对端是否一致\\n来源：0807设计表，3.2双机部署规范性检查表，第92行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 92,
          "originalName": "同步接口聚合口配置是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1100
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R093_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备设备VRID是否一致",
      "itemDescription": "VRID 取值为 1-255，同组虚拟路由器 VRID 保持一致，同组VRRP虚拟ip配置一致。\\n来源：0807设计表，3.2双机部署规范性检查表，第93行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 93,
          "originalName": "VRRP配置一致性：主备设备是否配置了相同的VRID和虚拟IP地址？",
          "part": 1,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1110
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R093_2",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备设备VRRP虚拟IP是否一致",
      "itemDescription": "VRID 取值为 1-255，同组虚拟路由器 VRID 保持一致，同组VRRP虚拟ip配置一致。\\n来源：0807设计表，3.2双机部署规范性检查表，第93行。原复合检查拆为独立作答项，保留原项名及说明。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 93,
          "originalName": "VRRP配置一致性：主备设备是否配置了相同的VRID和虚拟IP地址？",
          "part": 2,
          "split": true
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1120
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R094_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "物理接口IP与优先级：检查主备设备对应接口的物理IP，并确认优先级配置符合设计，监听接口/监听IP/BFD",
      "itemDescription": "根据现网设计需求定制主备优先级差异，满足客户主备切换需求。监听接口(板卡)/监听IP/BFD，优先级默认值 10（1-255)。\\n缺省情况下，监视 IP Down 之后减少的优先级为 10。\\n监视 IP，监视 BFD 不支持 IPv6。\\n来源：0807设计表，3.2双机部署规范性检查表，第94行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 94,
          "originalName": "物理接口IP与优先级：检查主备设备对应接口的物理IP，并确认优先级配置符合设计，监听接口/监听IP/BFD",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1130
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R095_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "Master/Backup状态验证：查看双机当前状态，确认与设计预期一致（例如，预期的设备是否为Master）",
      "itemDescription": "无非对称业务要求的场景，要保证正反向流量在同一台设备处理\\n来源：0807设计表，3.2双机部署规范性检查表，第95行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 95,
          "originalName": "Master/Backup状态验证：查看双机当前状态，确认与设计预期一致（例如，预期的设备是否为Master）",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1140
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R096_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机管理协议http/https/telnet/ssh开启状态是否一致",
      "itemDescription": "配置同步使用的是http/https/telnet/ssh协议，必须保证静默双机两端设备http/https/telnet/ssh配置一致\\n来源：0807设计表，3.2双机部署规范性检查表，第96行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 96,
          "originalName": "主备机管理协议http/https/telnet/ssh开启状态是否一致",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1150
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R097_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "配置同步功能检查",
      "itemDescription": "检查主备配置同步禁用开关是否关闭，默认开关关闭，此情况下配置同步才可正常。（功能位于 高可靠性--双机热备--配置同步参数 模块高级配置）\\n来源：0807设计表，3.2双机部署规范性检查表，第97行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 97,
          "originalName": "配置同步功能检查",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1160
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R098_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "主备机会话同步功能是否均开启",
      "itemDescription": "主备机会话同步配置必须一致。若会话同步未开启，则主备切换会导致部分长连接业务持续异常，此情况下需让客户手动重新拉起业务。\\n来源：0807设计表，3.2双机部署规范性检查表，第98行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 98,
          "originalName": "主备机会话同步功能是否均开启",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1170
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R099_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否存在非对称业务，非对称业务必须要开启会话同步非对称模",
      "itemDescription": "请根据现网情况决定是否需要开启非对称模式，非对称流量必须开启非对称模式，若现网流量不是非对称流量，请关闭非对称模式，否则非对称模式下备机会话会老化，若流量切到备机，因没有会话会造成断网问题。\\n来源：0807设计表，3.2双机部署规范性检查表，第99行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 99,
          "originalName": "是否存在非对称业务，非对称业务必须要开启会话同步非对称模",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1180
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R100_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置接口延迟up",
      "itemDescription": "主要用于框式设备，以防备机在重启过程中因业务板未重启完成的情况接口短暂up影响业务。页面高可靠性--接口延迟配置查看是否有配置，或者命令行show run | in linkup-delay查看是否存在接口延迟up配置，然后再确定是哪些接口有此配置\\n来源：0807设计表，3.2双机部署规范性检查表，第100行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 100,
          "originalName": "是否有配置接口延迟up",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1190
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R101_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否有配置监听业务板",
      "itemDescription": "主要用于框式设备，防止在单业务板场景下，主设备业务板重启，主备状态未切换而影响业务，默认需要配置\\nvrrp track board string [reduced 1-255]，业务板卡要监听所有的。每个业务板推荐值是10，按实际情况调整。假设该设备业务板坏了，导致设备状态切换，此时业务流量将不会走这台设备。\\n来源：0807设计表，3.2双机部署规范性检查表，第101行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 101,
          "originalName": "是否有配置监听业务板",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1200
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R102_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查上下游设备是否有配置静态ARP/ND、MAC/IP绑定",
      "itemDescription": "如果上下游设备配置静态ARP/ND或MAC/IP绑定功能，主备机切换后会导致业务不通（部分组网中会开启主备机接口的MAC地址同步，同样也需要检查上下游设备静态ARP/ND或MAC/IP绑定是否有绑定接口）\\n来源：0807设计表，3.2双机部署规范性检查表，第102行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 102,
          "originalName": "检查上下游设备是否有配置静态ARP/ND、MAC/IP绑定",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1210
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R103_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "检查上下游设备是否有开启学习免费ARP",
      "itemDescription": "部分厂商默认不开启免费ARP学习或者关闭了免费ARP学习功能，会导致备机切为主机后需要等ARP老化后重新学习流量才能通，影响双机的收敛时间\\n来源：0807设计表，3.2双机部署规范性检查表，第103行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 103,
          "originalName": "检查上下游设备是否有开启学习免费ARP",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1220
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R104_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "抢占模式检查：确认抢占模式（Preempt）的配置是否符合业务需求",
      "itemDescription": "抢占模式（抢占、非抢占）抢占延时:0-360000*10ms，根据客户需求配置抢占模式以及延时。主备回切若配置延时，则回切会按照配置的时间延时后再回切，在部分场景下不能配置该功能，例如割接过程中出现问题快速回退，如果配置了该功能，就无法迅速回切来达到恢复业务的目的（可以通过直接修改初始优先级来实现主备迅速回切）\\n来源：0807设计表，3.2双机部署规范性检查表，第104行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 104,
          "originalName": "抢占模式检查：确认抢占模式（Preempt）的配置是否符合业务需求",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1230
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R105_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "是否配置VRRP状态同步组",
      "itemDescription": "在对称业务场景，确保上下行的VRRP主备一致，默认需要配置\\nvrrp-sync-group string\\nmaster-vrrp interface 接口 vrid [reduced 1-255]\\n来源：0807设计表，3.2双机部署规范性检查表，第105行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 105,
          "originalName": "是否配置VRRP状态同步组",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1240
    },
    {
      "stableItemKey": "DUAL_NORMAL_DUAL_R106_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "认证模式配置",
      "itemDescription": "确保主备设备认证模式配置一致（无认证 / 明文认证 / MD5 认证），密码 / 加密规则匹配一致。\\n来源：0807设计表，3.2双机部署规范性检查表，第106行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 106,
          "originalName": "认证模式配置",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "NORMAL_DUAL",
      "enabled": true,
      "sortOrder": 1250
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R114_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "确认设备部署组网环境是否为旁路部署/串接部署",
      "itemDescription": "组网环境部署与集群配置有关联性，需要明确\\n来源：0807设计表，3.2双机部署规范性检查表，第114行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 114,
          "originalName": "确认设备部署组网环境是否为旁路部署/串接部署",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1260
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R115_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "确认设备组网是否为二层组网",
      "itemDescription": "集群二层组网模式下，虚服务IP与接口IP、交换机互连IP同网段，不需要配置IP集群\\n来源：0807设计表，3.2双机部署规范性检查表，第115行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 115,
          "originalName": "确认设备组网是否为二层组网",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1270
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R116_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "确认设备组网是否为三层组网、虚服务IP与接口IP是否同网段",
      "itemDescription": "集群三层组网模式下，虚服务IP与接口IP、交换机互连IP不同网段，需要配置IP集群\\n来源：0807设计表，3.2双机部署规范性检查表，第116行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 116,
          "originalName": "确认设备组网是否为三层组网、虚服务IP与接口IP是否同网段",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1280
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R117_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "虚服务是否关联集群",
      "itemDescription": "虚服务若未关联集群，发生主备切换时虚服务不会发送免费arp，可能导致对端交换机arp无法切换影响业务\\n来源：0807设计表，3.2双机部署规范性检查表，第117行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 117,
          "originalName": "虚服务是否关联集群",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1290
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R118_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "SNAT若使用地址池，是否关联集群",
      "itemDescription": "SNAT地址池若未关联集群，发生主备切换时地址池地址不会发送免费arp，可能导致对端交换机arp无法切换影响业务\\n来源：0807设计表，3.2双机部署规范性检查表，第118行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 118,
          "originalName": "SNAT若使用地址池，是否关联集群",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1300
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R119_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "现场是否有vrrp配置",
      "itemDescription": "集群配置与VRRP配置冲突，两种配置无法同时使用\\n来源：0807设计表，3.2双机部署规范性检查表，第119行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 119,
          "originalName": "现场是否有vrrp配置",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1310
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R120_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "设备集群配置时，要求所有设备为同一时间",
      "itemDescription": "建议配置集群前先配置NTP时间同步，设备集群在设备时间不同时无法配置\\n来源：0807设计表，3.2双机部署规范性检查表，第120行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 120,
          "originalName": "设备集群配置时，要求所有设备为同一时间",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1320
    },
    {
      "stableItemKey": "DUAL_CLUSTER_R121_1",
      "itemType": "DUAL_MACHINE_CHECK",
      "businessCategoryCode": "DUAL_DEPLOYMENT_CHECK",
      "itemName": "设备部署完成后，确认设备的配置是否同步",
      "itemDescription": "若设备集群配置不同步，发生主备切换后可能因为配置不同影响业务\\n来源：0807设计表，3.2双机部署规范性检查表，第121行。",
      "interfaceFormat": "TABLE",
      "interfaceSchema": {
        "fields": [
          {
            "code": "executed",
            "label": "是否已核查",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "normal",
            "label": "是否符合本次部署要求",
            "type": "BOOLEAN",
            "required": true
          },
          {
            "code": "remark",
            "label": "事实说明及特殊情况备注",
            "type": "TEXT",
            "required": true,
            "visibleWhen": {
              "field": "normal",
              "equals": false
            }
          }
        ],
        "source": {
          "file": "4、割接平台设计-数据整理（0807).xlsx",
          "sheet": "3.2双机部署规范性检查表",
          "row": 121,
          "originalName": "设备部署完成后，确认设备的配置是否同步",
          "part": 1,
          "split": false
        }
      },
      "feedbackFormat": "BOOLEAN_REMARK",
      "required": true,
      "workMode": "DIRECT",
      "subtableCode": "CLUSTER",
      "enabled": true,
      "sortOrder": 1330
    }
  ],
  "bindingRules": [
    {
      "stableRuleKey": "RULE_SURVEY_CUTOVER_BACKGROUND",
      "stableItemKey": "SURVEY_CUTOVER_BACKGROUND",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_BUSINESS_SUMMARY",
      "stableItemKey": "SURVEY_BUSINESS_SUMMARY",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_IMPACT_SCOPE",
      "stableItemKey": "SURVEY_IMPACT_SCOPE",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_CONTINUITY_REQUIREMENT",
      "stableItemKey": "SURVEY_CONTINUITY_REQUIREMENT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_INTERRUPTION_COUNT",
      "stableItemKey": "SURVEY_INTERRUPTION_COUNT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_CURRENT_TOPOLOGY",
      "stableItemKey": "SURVEY_CURRENT_TOPOLOGY",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_DEVICE_LOCATION_PLAN",
      "stableItemKey": "SURVEY_DEVICE_LOCATION_PLAN",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "NETWORK_TOPOLOGY_CHANGE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_INTERFACE_INTERCONNECT_PLAN",
      "stableItemKey": "SURVEY_INTERFACE_INTERCONNECT_PLAN",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "NETWORK_TOPOLOGY_CHANGE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_IP_VLAN_PLAN",
      "stableItemKey": "SURVEY_IP_VLAN_PLAN",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "NETWORK_TOPOLOGY_CHANGE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_PERFORMANCE_BASELINE",
      "stableItemKey": "SURVEY_PERFORMANCE_BASELINE",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_CONNECTIVITY_TEST_CASE",
      "stableItemKey": "SURVEY_CONNECTIVITY_TEST_CASE",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_SURVEY_VENDOR_CONFIG_TRANSLATION",
      "stableItemKey": "SURVEY_VENDOR_CONFIG_TRANSLATION",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_VENDOR"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_CURRENT_VERSION_BULLETIN",
      "stableItemKey": "RISK_CURRENT_VERSION_BULLETIN",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_TARGET_VERSION_BULLETIN",
      "stableItemKey": "RISK_TARGET_VERSION_BULLETIN",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_DUAL_CONFIG_CONSISTENCY",
      "stableItemKey": "RISK_DUAL_CONFIG_CONSISTENCY",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL",
          "SILENT_DUAL",
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_FILTER_NAT_QOS_COMPILE_COUNT",
      "stableItemKey": "RISK_FILTER_NAT_QOS_COMPILE_COUNT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_COMPILE_LIMIT_ASSESSMENT",
      "stableItemKey": "RISK_COMPILE_LIMIT_ASSESSMENT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_SESSION_SYNC",
      "stableItemKey": "RISK_SESSION_SYNC",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL",
          "SILENT_DUAL",
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_DUAL_CONTROLLER_VERSION",
      "stableItemKey": "RISK_DUAL_CONTROLLER_VERSION",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_BOARD",
          "VERSION_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_PACKAGE_MD5",
      "stableItemKey": "RISK_PACKAGE_MD5",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_MAJOR_PROJECT_SPARES",
      "stableItemKey": "RISK_MAJOR_PROJECT_SPARES",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_SYSTEM_LOG",
      "stableItemKey": "RISK_SYSTEM_LOG",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_DIAGNOSTIC_LOG",
      "stableItemKey": "RISK_DIAGNOSTIC_LOG",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_RUNNING_VERSION_BACKUP",
      "stableItemKey": "RISK_RUNNING_VERSION_BACKUP",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_HOT_PATCH_BACKUP",
      "stableItemKey": "RISK_HOT_PATCH_BACKUP",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_LICENSE_BACKUP",
      "stableItemKey": "RISK_LICENSE_BACKUP",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_CONFIG_BACKUP",
      "stableItemKey": "RISK_CONFIG_BACKUP",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_DYNAMIC_TABLE_COLLECTION",
      "stableItemKey": "RISK_DYNAMIC_TABLE_COLLECTION",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_MTU_JUMBO_FRAME",
      "stableItemKey": "RISK_MTU_JUMBO_FRAME",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_HUNDRED_G_FEC",
      "stableItemKey": "RISK_HUNDRED_G_FEC",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_LONG_CONNECTION",
      "stableItemKey": "RISK_LONG_CONNECTION",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_SECOND_PASS_DEVICE",
      "stableItemKey": "RISK_SECOND_PASS_DEVICE",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_STP",
      "stableItemKey": "RISK_STP",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_F5_DEFAULT",
      "stableItemKey": "RISK_F5_DEFAULT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_ADWARE_DEFAULT",
      "stableItemKey": "RISK_ADWARE_DEFAULT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_RISK_ROOM_OPERATION_COMMITMENT",
      "stableItemKey": "RISK_ROOM_OPERATION_COMMITMENT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "DEVICE_REPLACE_WHOLE",
          "DEVICE_REPLACE_BOARD",
          "DEVICE_REPLACE_VENDOR",
          "DEVICE_ONBOARD",
          "VERSION_UPGRADE",
          "DISASTER_RECOVERY_DRILL",
          "CONFIGURATION_CHANGE",
          "NETWORK_TOPOLOGY_CHANGE",
          "VERSION_PATCH",
          "SIGNATURE_UPGRADE"
        ],
        "DEVICE_TYPE": [
          "FW",
          "SW",
          "ADX",
          "DPX"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R003_1",
      "stableItemKey": "DUAL_VSM_R003_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R004_1",
      "stableItemKey": "DUAL_VSM_R004_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R005_1",
      "stableItemKey": "DUAL_VSM_R005_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R006_1",
      "stableItemKey": "DUAL_VSM_R006_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R007_1",
      "stableItemKey": "DUAL_VSM_R007_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R008_1",
      "stableItemKey": "DUAL_VSM_R008_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R009_1",
      "stableItemKey": "DUAL_VSM_R009_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R010_1",
      "stableItemKey": "DUAL_VSM_R010_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R011_1",
      "stableItemKey": "DUAL_VSM_R011_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R012_1",
      "stableItemKey": "DUAL_VSM_R012_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R012_2",
      "stableItemKey": "DUAL_VSM_R012_2",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R013_1",
      "stableItemKey": "DUAL_VSM_R013_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R014_1",
      "stableItemKey": "DUAL_VSM_R014_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R015_1",
      "stableItemKey": "DUAL_VSM_R015_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R015_2",
      "stableItemKey": "DUAL_VSM_R015_2",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R016_1",
      "stableItemKey": "DUAL_VSM_R016_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_VSM_R017_1",
      "stableItemKey": "DUAL_VSM_R017_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R025_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R025_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R026_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R026_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R027_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R027_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R028_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R028_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R029_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R029_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R030_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R030_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R031_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R031_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R032_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R032_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R033_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R033_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R033_2",
      "stableItemKey": "DUAL_SILENT_DUAL_R033_2",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R034_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R034_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R035_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R035_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R036_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R036_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R037_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R037_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R038_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R038_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R039_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R039_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R040_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R040_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R041_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R041_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R042_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R042_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R043_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R043_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R044_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R044_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R045_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R045_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R046_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R046_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R047_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R047_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_SILENT_DUAL_R048_1",
      "stableItemKey": "DUAL_SILENT_DUAL_R048_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "SILENT_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R056_1",
      "stableItemKey": "DUAL_DRP_DUAL_R056_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R057_1",
      "stableItemKey": "DUAL_DRP_DUAL_R057_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R058_1",
      "stableItemKey": "DUAL_DRP_DUAL_R058_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R059_1",
      "stableItemKey": "DUAL_DRP_DUAL_R059_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R060_1",
      "stableItemKey": "DUAL_DRP_DUAL_R060_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R061_1",
      "stableItemKey": "DUAL_DRP_DUAL_R061_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R062_1",
      "stableItemKey": "DUAL_DRP_DUAL_R062_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R063_1",
      "stableItemKey": "DUAL_DRP_DUAL_R063_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R064_1",
      "stableItemKey": "DUAL_DRP_DUAL_R064_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R065_1",
      "stableItemKey": "DUAL_DRP_DUAL_R065_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R066_1",
      "stableItemKey": "DUAL_DRP_DUAL_R066_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R067_1",
      "stableItemKey": "DUAL_DRP_DUAL_R067_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R068_1",
      "stableItemKey": "DUAL_DRP_DUAL_R068_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R069_1",
      "stableItemKey": "DUAL_DRP_DUAL_R069_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R070_1",
      "stableItemKey": "DUAL_DRP_DUAL_R070_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R071_1",
      "stableItemKey": "DUAL_DRP_DUAL_R071_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R071_2",
      "stableItemKey": "DUAL_DRP_DUAL_R071_2",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R071_3",
      "stableItemKey": "DUAL_DRP_DUAL_R071_3",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R072_1",
      "stableItemKey": "DUAL_DRP_DUAL_R072_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R073_1",
      "stableItemKey": "DUAL_DRP_DUAL_R073_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R074_1",
      "stableItemKey": "DUAL_DRP_DUAL_R074_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R075_1",
      "stableItemKey": "DUAL_DRP_DUAL_R075_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_DRP_DUAL_R076_1",
      "stableItemKey": "DUAL_DRP_DUAL_R076_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "DRP_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R084_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R084_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R085_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R085_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R086_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R086_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R087_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R087_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R088_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R088_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R089_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R089_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R090_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R090_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R091_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R091_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R092_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R092_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R093_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R093_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R093_2",
      "stableItemKey": "DUAL_NORMAL_DUAL_R093_2",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R094_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R094_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R095_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R095_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R096_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R096_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R097_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R097_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R098_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R098_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R099_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R099_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R100_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R100_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R101_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R101_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R102_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R102_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R103_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R103_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R104_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R104_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R105_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R105_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_NORMAL_DUAL_R106_1",
      "stableItemKey": "DUAL_NORMAL_DUAL_R106_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "NORMAL_DUAL"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R114_1",
      "stableItemKey": "DUAL_CLUSTER_R114_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R115_1",
      "stableItemKey": "DUAL_CLUSTER_R115_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R116_1",
      "stableItemKey": "DUAL_CLUSTER_R116_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R117_1",
      "stableItemKey": "DUAL_CLUSTER_R117_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R118_1",
      "stableItemKey": "DUAL_CLUSTER_R118_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R119_1",
      "stableItemKey": "DUAL_CLUSTER_R119_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R120_1",
      "stableItemKey": "DUAL_CLUSTER_R120_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_DUAL_CLUSTER_R121_1",
      "stableItemKey": "DUAL_CLUSTER_R121_1",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "CLUSTER"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 50,
      "requiredResult": true,
      "enabled": true
    }
  ],
  "exampleItems": [
    {
      "stableItemKey": "EXAMPLE_EXACT",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "BUSINESS_SUMMARY",
      "itemName": "示例：精确组合调研",
      "itemDescription": "独立配置草稿的匹配演示项；不进入标准发布配置。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "maxLength": 1000
      },
      "feedbackFormat": "TEXT",
      "required": false,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 2001
    },
    {
      "stableItemKey": "EXAMPLE_PARTIAL",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "BUSINESS_SUMMARY",
      "itemName": "示例：部分维度限定",
      "itemDescription": "独立配置草稿的匹配演示项；不进入标准发布配置。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "maxLength": 1000
      },
      "feedbackFormat": "TEXT",
      "required": false,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 2002
    },
    {
      "stableItemKey": "EXAMPLE_PRIORITY",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "BUSINESS_SUMMARY",
      "itemName": "示例：高优先级覆盖必填性",
      "itemDescription": "独立配置草稿的匹配演示项；不进入标准发布配置。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "maxLength": 1000
      },
      "feedbackFormat": "TEXT",
      "required": false,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": true,
      "sortOrder": 2003
    },
    {
      "stableItemKey": "EXAMPLE_DISABLED",
      "itemType": "BUSINESS_SURVEY",
      "businessCategoryCode": "BUSINESS_SUMMARY",
      "itemName": "示例：停用项不参与",
      "itemDescription": "独立配置草稿的匹配演示项；不进入标准发布配置。",
      "interfaceFormat": "INPUT",
      "interfaceSchema": {
        "maxLength": 1000
      },
      "feedbackFormat": "TEXT",
      "required": false,
      "workMode": "DIRECT",
      "subtableCode": null,
      "enabled": false,
      "sortOrder": 2004
    }
  ],
  "exampleRules": [
    {
      "stableRuleKey": "RULE_EXAMPLE_EXACT",
      "stableItemKey": "EXAMPLE_EXACT",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE"
        ],
        "NETWORK_MODE": [
          "VSM"
        ],
        "DEVICE_TYPE": [
          "FW"
        ],
        "CUTOVER_LEVEL": [
          "A"
        ]
      },
      "priority": 10,
      "requiredResult": false,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_EXAMPLE_PARTIAL",
      "stableItemKey": "EXAMPLE_PARTIAL",
      "dimensionConditions": {
        "NETWORK_MODE": [
          "VSM"
        ],
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 10,
      "requiredResult": false,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_EXAMPLE_PRIORITY",
      "stableItemKey": "EXAMPLE_PRIORITY",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A"
        ]
      },
      "priority": 10,
      "requiredResult": true,
      "enabled": true
    },
    {
      "stableRuleKey": "RULE_EXAMPLE_DISABLED",
      "stableItemKey": "EXAMPLE_DISABLED",
      "dimensionConditions": {
        "CUTOVER_LEVEL": [
          "A",
          "B",
          "C"
        ]
      },
      "priority": 10,
      "requiredResult": false,
      "enabled": false
    },
    {
      "stableRuleKey": "RULE_EXAMPLE_PRIORITY_HIGH",
      "stableItemKey": "EXAMPLE_PRIORITY",
      "dimensionConditions": {
        "CUTOVER_TYPE": [
          "VERSION_UPGRADE"
        ],
        "CUTOVER_LEVEL": [
          "A"
        ]
      },
      "priority": 100,
      "requiredResult": false,
      "enabled": true
    }
  ]
}';
-- END CUTOVER CONFIGURATION JSON

INSERT INTO cut_cutover_configuration_revision
(id,configuration_code,configuration_name,revision_no,status_code,effective_from,dictionary_snapshot,dimension_definition_snapshot,plan_template_section_snapshot,navigation_rule_snapshot,validation_result_snapshot,change_summary,published_by,published_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000442001,'CUTOVER_STANDARD_V1','割接标准配置（V1/V2）',1,'PUBLISHED',NOW(),
JSON_EXTRACT(@cut_seed,'$.dictionarySnapshot'),JSON_EXTRACT(@cut_seed,'$.dimensions'),
JSON_EXTRACT(@cut_seed,'$.planTemplateSections'),JSON_EXTRACT(@cut_seed,'$.navigationRule'),JSON_ARRAY(),
'割接专项完整配置；来源及复合检查拆分记录保存在每项Schema内',1,NOW(),0,
'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_configuration_revision WHERE configuration_code='CUTOVER_STANDARD_V1' AND revision_no=1 AND tenant_id=1);

INSERT INTO cut_cutover_checklist_item_definition_revision
(id,configuration_revision_id,stable_item_key,item_definition_version,item_type_code,business_category_code,item_name,item_description,
interface_format_code,interface_schema,feedback_format_code,required_flag,work_mode_code,external_source_config,subtable_code,status_code,
sort_order,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000442001+100+j.ordinality,970000000000442001,j.item_key,1,j.item_type,j.category,j.item_name,j.description,
j.format,j.item_schema,j.feedback,j.required_flag,j.work_mode,NULL,j.subtable,IF(j.enabled_flag=1,'ENABLED','DISABLED'),
j.sort_order,0,'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
FROM JSON_TABLE(JSON_EXTRACT(@cut_seed,'$.items'),'$[*]' COLUMNS (
 ordinality FOR ORDINALITY, item_key VARCHAR(128) PATH '$.stableItemKey',item_type VARCHAR(32) PATH '$.itemType',
 category VARCHAR(64) PATH '$.businessCategoryCode',item_name VARCHAR(255) PATH '$.itemName',description TEXT PATH '$.itemDescription',
 format VARCHAR(32) PATH '$.interfaceFormat',item_schema JSON PATH '$.interfaceSchema',feedback VARCHAR(32) PATH '$.feedbackFormat',
 required_flag INT PATH '$.required',work_mode VARCHAR(32) PATH '$.workMode',subtable VARCHAR(64) PATH '$.subtableCode' NULL ON EMPTY,
 enabled_flag INT PATH '$.enabled',sort_order INT PATH '$.sortOrder')) j
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_checklist_item_definition_revision old
 WHERE old.configuration_revision_id=970000000000442001 AND old.stable_item_key=j.item_key AND old.tenant_id=1);

INSERT INTO cut_cutover_checklist_binding_rule_revision
(id,configuration_revision_id,stable_rule_key,item_definition_id,item_definition_version,dimension_condition_snapshot,priority,required_result,status_code,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000442001+1000+j.ordinality,970000000000442001,j.rule_key,item.id,1,j.conditions,j.priority,j.required_flag,
IF(j.enabled_flag=1,'ENABLED','DISABLED'),0,'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
FROM JSON_TABLE(JSON_EXTRACT(@cut_seed,'$.bindingRules'),'$[*]' COLUMNS (
 ordinality FOR ORDINALITY,rule_key VARCHAR(128) PATH '$.stableRuleKey',item_key VARCHAR(128) PATH '$.stableItemKey',
 conditions JSON PATH '$.dimensionConditions',priority INT PATH '$.priority',required_flag INT PATH '$.requiredResult',enabled_flag INT PATH '$.enabled')) j
JOIN cut_cutover_checklist_item_definition_revision item ON item.configuration_revision_id=970000000000442001 AND item.stable_item_key=j.item_key AND item.tenant_id=1
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_checklist_binding_rule_revision old
 WHERE old.configuration_revision_id=970000000000442001 AND old.stable_rule_key=j.rule_key AND old.tenant_id=1);

INSERT INTO cut_cutover_configuration_revision
(id,configuration_code,configuration_name,revision_no,status_code,effective_from,dictionary_snapshot,dimension_definition_snapshot,plan_template_section_snapshot,navigation_rule_snapshot,validation_result_snapshot,change_summary,published_by,published_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000452001,'CUTOVER_EXAMPLE_V1','割接组合匹配示例草稿',1,'DRAFT',NULL,
JSON_EXTRACT(@cut_seed,'$.dictionarySnapshot'),JSON_EXTRACT(@cut_seed,'$.dimensions'),
JSON_EXTRACT(@cut_seed,'$.planTemplateSections'),JSON_EXTRACT(@cut_seed,'$.navigationRule'),JSON_ARRAY(),
'割接专项完整配置；来源及复合检查拆分记录保存在每项Schema内',NULL,NULL,0,
'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_configuration_revision WHERE configuration_code='CUTOVER_EXAMPLE_V1' AND revision_no=1 AND tenant_id=1);

INSERT INTO cut_cutover_checklist_item_definition_revision
(id,configuration_revision_id,stable_item_key,item_definition_version,item_type_code,business_category_code,item_name,item_description,
interface_format_code,interface_schema,feedback_format_code,required_flag,work_mode_code,external_source_config,subtable_code,status_code,
sort_order,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000452001+100+j.ordinality,970000000000452001,j.item_key,1,j.item_type,j.category,j.item_name,j.description,
j.format,j.item_schema,j.feedback,j.required_flag,j.work_mode,NULL,j.subtable,IF(j.enabled_flag=1,'ENABLED','DISABLED'),
j.sort_order,0,'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
FROM JSON_TABLE(JSON_MERGE_PRESERVE(JSON_EXTRACT(@cut_seed,'$.items'),JSON_EXTRACT(@cut_seed,'$.exampleItems')),'$[*]' COLUMNS (
 ordinality FOR ORDINALITY, item_key VARCHAR(128) PATH '$.stableItemKey',item_type VARCHAR(32) PATH '$.itemType',
 category VARCHAR(64) PATH '$.businessCategoryCode',item_name VARCHAR(255) PATH '$.itemName',description TEXT PATH '$.itemDescription',
 format VARCHAR(32) PATH '$.interfaceFormat',item_schema JSON PATH '$.interfaceSchema',feedback VARCHAR(32) PATH '$.feedbackFormat',
 required_flag INT PATH '$.required',work_mode VARCHAR(32) PATH '$.workMode',subtable VARCHAR(64) PATH '$.subtableCode' NULL ON EMPTY,
 enabled_flag INT PATH '$.enabled',sort_order INT PATH '$.sortOrder')) j
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_checklist_item_definition_revision old
 WHERE old.configuration_revision_id=970000000000452001 AND old.stable_item_key=j.item_key AND old.tenant_id=1);

INSERT INTO cut_cutover_checklist_binding_rule_revision
(id,configuration_revision_id,stable_rule_key,item_definition_id,item_definition_version,dimension_condition_snapshot,priority,required_result,status_code,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 970000000000452001+1000+j.ordinality,970000000000452001,j.rule_key,item.id,1,j.conditions,j.priority,j.required_flag,
IF(j.enabled_flag=1,'ENABLED','DISABLED'),0,'cut-special-20261012',NOW(),'cut-special-20261012',NOW(),b'0',1
FROM JSON_TABLE(JSON_MERGE_PRESERVE(JSON_EXTRACT(@cut_seed,'$.bindingRules'),JSON_EXTRACT(@cut_seed,'$.exampleRules')),'$[*]' COLUMNS (
 ordinality FOR ORDINALITY,rule_key VARCHAR(128) PATH '$.stableRuleKey',item_key VARCHAR(128) PATH '$.stableItemKey',
 conditions JSON PATH '$.dimensionConditions',priority INT PATH '$.priority',required_flag INT PATH '$.requiredResult',enabled_flag INT PATH '$.enabled')) j
JOIN cut_cutover_checklist_item_definition_revision item ON item.configuration_revision_id=970000000000452001 AND item.stable_item_key=j.item_key AND item.tenant_id=1
WHERE NOT EXISTS (SELECT 1 FROM cut_cutover_checklist_binding_rule_revision old
 WHERE old.configuration_revision_id=970000000000452001 AND old.stable_rule_key=j.rule_key AND old.tenant_id=1);

SET @cut_seed = NULL;
