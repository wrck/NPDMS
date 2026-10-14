package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverExternalDataPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverExternalDataPort.Field;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** 接口选项仅来自实际注册的正式 Provider，不创建虚构接口或本地公告源。 */
@Service
@RequiredArgsConstructor
public class CutoverExternalDataDirectory {
    private final ObjectProvider<CutoverExternalDataPort> providers;

    public record Catalog(List<CutoverExternalDataPort.Definition> interfaces, List<Field> contextFields,
                          List<Field> bulletinFields) { }

    public Catalog catalog() {
        var interfaces = providers.orderedStream().map(CutoverExternalDataPort::definition).toList();
        if (interfaces.stream().map(CutoverExternalDataPort.Definition::code).distinct().count() != interfaces.size()) {
            throw new IllegalStateException("外部接口目录存在重复编码");
        }
        return new Catalog(interfaces, List.of(
                new Field("softwareVersion", "本项查询软件版本", "STRING", true),
                new Field("device.id", "设备ID", "NUMBER", true),
                new Field("device.sn", "设备序列号", "STRING", true),
                new Field("device.productModel", "设备型号", "STRING", false),
                new Field("device.currentSoftwareVersion", "当前软件版本", "STRING", false),
                new Field("device.targetSoftwareVersion", "目标软件版本", "STRING", false),
                new Field("task.id", "割接任务ID", "NUMBER", true),
                new Field("project.id", "项目ID", "NUMBER", true)), List.of(
                new Field("number", "公告编号", "STRING", true), new Field("subject", "主题", "STRING", true),
                new Field("description", "描述", "STRING", true), new Field("solution", "解决方案", "STRING", true)));
    }

    public Optional<CutoverExternalDataPort> find(String code) {
        var matches = providers.orderedStream().filter(provider -> provider.definition().code().equals(code)).toList();
        if (matches.size() > 1) throw new IllegalStateException("外部接口目录存在重复编码");
        return matches.stream().findFirst();
    }
}
