-- P12最新裁决的生产来源查询；待模板/适配器/Owner改造，未执行。
-- 只移除产品扩展定义投影/参数，不以CRM异名为来源排除条件。
SELECT i.id,i.item,i.itemname,i.describe_ FROM fb_items i WHERE (:productCodes='' OR FIND_IN_SET(i.item,:productCodes));
