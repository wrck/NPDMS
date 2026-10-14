-- P12 / fb_items | 原任务 2108534088732168194 v2
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT i.id,i.item,i.itemname,i.describe_,:migrationDefinitionRevisionId AS migrationDefinitionRevisionId FROM fb_items i WHERE (:productCodes='' OR FIND_IN_SET(i.item,:productCodes));
