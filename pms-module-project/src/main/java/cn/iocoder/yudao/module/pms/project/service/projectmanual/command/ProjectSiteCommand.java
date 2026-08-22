package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

/** 创建项目时引用的 AST 站点及客户端读取版本。 */
public record ProjectSiteCommand(Long siteId, Integer siteVersion, Boolean primarySite) {
}
