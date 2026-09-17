package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingAggregate;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingDocumentArtifact;

/**
 * 交底生成的应用端口，不是新文件 Owner 或通用文档引擎。
 * 生产适配器须通过既有 SOL/PROJ/AST/PLT 契约解析模板和来源、校验权限并核验实际文件；
 * 请求中的模板和快照只是选择/编辑输入，不能直接当成已确认权威事实。
 * 当前尚无经验证的生产适配器；不得提供自动成功的默认实现。
 */
public interface BriefingGenerationPort {
    record Request(BriefingAggregate.Identity identity, int inputVersion, Long requestedTemplateId,
                   String requestedSourceSnapshot, String draftContent, Long actorId) { }

    /** 同一身份、版本及相同输入重试须复用可追溯结果；文件副作用须能对账，不受数据库回滚假设保护。 */
    BriefingDocumentArtifact generate(Request request);

    /**
     * 核验实际文件存在、内容哈希、冻结来源和调用方文件权限；无法核实时抛错。
     * artifact.inputVersion 只用于当前业务命令并发校验，不冒充生成时的文件版本。
     */
    void verify(BriefingDocumentArtifact artifact, Long actorId);
}
