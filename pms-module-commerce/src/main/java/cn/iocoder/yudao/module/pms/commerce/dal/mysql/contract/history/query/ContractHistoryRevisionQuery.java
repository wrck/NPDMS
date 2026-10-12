package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.query;
public record ContractHistoryRevisionQuery(Long tenantId,Long projectId,Long sourceRowId,String sourceRevisionHash) {}
