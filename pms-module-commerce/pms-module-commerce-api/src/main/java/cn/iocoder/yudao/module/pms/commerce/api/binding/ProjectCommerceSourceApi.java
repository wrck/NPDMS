package cn.iocoder.yudao.module.pms.commerce.api.binding;

/**
 * 项目创建时的商务来源绑定（合同主档单入口）。
 * ADR-0032：实现必须以 {@code Propagation.MANDATORY} 加入调用方创建事务，任何拒绝整体回滚。
 */
public interface ProjectCommerceSourceApi {

    /**
     * 绑定项目与合同主档（com_project_contract_relation，role=RELATED、sourceSystem=PMS、
     * record_key=operationId），并回填取值链上执行单的 primary_project_id。
     * 写入前按 ADR-0038 重新校验创建人当前公司范围含合同公司编码并审计授权快照；
     * 合同已关联其他项目、执行单已绑定其他项目时拒绝；同身份重放幂等。
     */
    void bindProjectCommerceSource(ProjectCommerceSourceBindCommand command);

    /**
     * 只读解析合同→订单（排退货）→执行单取值链并给出建议值。
     * 访问规则与合同详情一致（ADR-0038 公司范围，空范围拒绝）；不校验项目侧状态。
     */
    CreationSourceResolution resolveCreationSource(ProjectCommerceSourceResolveCommand command);

    /** 只读解析命令：subjectUserId=创建人（公司范围主体）。 */
    record ProjectCommerceSourceResolveCommand(Long tenantId, Long contractId, Long subjectUserId) {
    }

    /** 解析结果：contract 为主档事实，resolved 为按老系统规则计算的建议值（四维/重大级别/客户项目名称为CRM权威值）。 */
    record CreationSourceResolution(Long contractId, String contractNo, String contractName,
                                    String companyCode, String companyName,
                                    String projectName, String customerProjectName, String majorProjectLevel,
                                    String projectType, String marketCode, String marketName,
                                    String systemCode, String systemName, String expendCode, String expendName,
                                    String industryCode, String industryName,
                                    Long executionOrderId, String executionNo) {
    }
}
