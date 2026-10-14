-- P11 / PRODUCT | 原任务 2102221608179109889 v1
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT itemCode,itemModel,itemDesc,remark,statecode,updateTime FROM (SELECT itemCode,itemModel,itemDesc,remark,statecode,updateTime,ROW_NUMBER() OVER (PARTITION BY itemCode ORDER BY updateTime DESC,createTime DESC,itemModel DESC) AS row_rank FROM DPtech_v_product_info_single) ranked WHERE row_rank=1;
