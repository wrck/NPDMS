package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.List;
import java.util.Set;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_ARGUMENT_INVALID;

/** Declarative print settings only: no scripts, HTML, external images or template expressions. */
public record TrainingPrintLayout(String title, String header, String footer, String paper,
                                  boolean landscape, int fontSize, int margin, List<String> sections) {
    public static final long DEFAULT_TEMPLATE_ID = 992209200101L;
    public static final List<String> SECTIONS = List.of("BASIC", "CONTENT", "CONFIRMATION", "REMARK");
    public static TrainingPrintLayout defaults() {
        return new TrainingPrintLayout("现场培训记录表", "", "", "A4", false, 11, 40, SECTIONS);
    }
    public static TrainingPrintLayout parse(String json) {
        try {
            TrainingPrintLayout layout = JsonUtils.parseObject(json, TrainingPrintLayout.class);
            if (layout == null || layout.title() == null || layout.title().isBlank() || layout.title().length() > 80
                    || layout.header() == null || layout.header().length() > 100
                    || layout.footer() == null || layout.footer().length() > 100
                    || layout.header().contains("\n") || layout.footer().contains("\n")
                    || !Set.of("A4", "LETTER").contains(layout.paper())
                    || layout.fontSize() < 9 || layout.fontSize() > 16 || layout.margin() < 24 || layout.margin() > 72
                    || layout.sections() == null || layout.sections().size() != 4
                    || !Set.copyOf(layout.sections()).equals(Set.copyOf(SECTIONS))) throw new IllegalArgumentException();
            return layout;
        } catch (Exception invalid) {
            throw new ServiceException(TRAINING_ARGUMENT_INVALID.getCode(), "打印模板无效，请检查标题、页眉页脚、纸张、字号、页边距及完整分区顺序");
        }
    }
}
