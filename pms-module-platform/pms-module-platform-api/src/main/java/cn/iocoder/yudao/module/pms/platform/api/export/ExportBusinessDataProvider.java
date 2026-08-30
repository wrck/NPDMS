package cn.iocoder.yudao.module.pms.platform.api.export;

/** 业务Owner提供给PLT的导出裁剪SPI。 */
public interface ExportBusinessDataProvider {

    String ownerContext();

    String exportType();

    ExportBusinessDataSnapshot inspect(ExportBusinessDataQuery query);

    ExportBusinessDataSnapshot generate(ExportBusinessDataQuery query);
}
