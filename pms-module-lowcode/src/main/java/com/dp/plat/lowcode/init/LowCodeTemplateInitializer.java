package com.dp.plat.lowcode.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.entity.LowCodeList;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;
import com.dp.plat.lowcode.entity.LowCodeTab;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeListService;
import com.dp.plat.lowcode.service.LowCodeRelatedPageService;
import com.dp.plat.lowcode.service.LowCodeTabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 低代码模板初始化器。
 *
 * <p>应用启动时分别检查低代码表（{@code pms_lowcode_form}、{@code pms_lowcode_list}、
 * {@code pms_lowcode_tab}、{@code pms_lowcode_related_page}）是否为空，
 * 为空则加载对应 classpath:lowcode-templates/{form,list,tab,related-page}/ 下的预置模板 JSON，
 * 通过各自的 {@code importConfig} 方法导入数据库。</p>
 *
 * <p>预置表单模板：</p>
 * <ul>
 *   <li>{@code project-create.template.json} — 项目创建表单</li>
 *   <li>{@code asset-inbound.template.json} — 资产入库表单</li>
 *   <li>{@code settlement-create.template.json} — 结算创建表单</li>
 * </ul>
 *
 * <p>预置列表模板：</p>
 * <ul>
 *   <li>{@code project-list.template.json} — 项目列表</li>
 *   <li>{@code asset-list.template.json} — 资产列表</li>
 *   <li>{@code settlement-list.template.json} — 结算列表</li>
 * </ul>
 *
 * <p>预置标签页模板：</p>
 * <ul>
 *   <li>{@code project-detail-tabs.template.json} — 项目详情标签页（8 个 Tab）</li>
 *   <li>{@code asset-detail-tabs.template.json} — 资产详情标签页（4 个 Tab）</li>
 * </ul>
 *
 * <p>预置关联页模板：</p>
 * <ul>
 *   <li>{@code project-overview.template.json} — 项目概览关联页（4 个区块）</li>
 *   <li>{@code asset-overview.template.json} — 资产概览关联页（4 个区块）</li>
 * </ul>
 *
 * <p>导入采用 {@code importConfig}：若 code 冲突会自动追加数字后缀，
 * 因此重复启动不会抛错也不会污染已有配置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowCodeTemplateInitializer implements ApplicationRunner {

    /** 表单模板所在 classpath 目录 */
    private static final String FORM_TEMPLATE_DIR = "lowcode-templates/form/";

    /** 列表模板所在 classpath 目录 */
    private static final String LIST_TEMPLATE_DIR = "lowcode-templates/list/";

    /** 标签页模板所在 classpath 目录 */
    private static final String TAB_TEMPLATE_DIR = "lowcode-templates/tab/";

    /** 关联页模板所在 classpath 目录 */
    private static final String RELATED_PAGE_TEMPLATE_DIR = "lowcode-templates/related-page/";

    /** 预置表单模板文件名列表 */
    private static final List<String> FORM_TEMPLATE_FILES = List.of(
            "project-create.template.json",
            "asset-inbound.template.json",
            "settlement-create.template.json"
    );

    /** 预置列表模板文件名列表 */
    private static final List<String> LIST_TEMPLATE_FILES = List.of(
            "project-list.template.json",
            "asset-list.template.json",
            "settlement-list.template.json"
    );

    /** 预置标签页模板文件名列表 */
    private static final List<String> TAB_TEMPLATE_FILES = List.of(
            "project-detail-tabs.template.json",
            "asset-detail-tabs.template.json"
    );

    /** 预置关联页模板文件名列表 */
    private static final List<String> RELATED_PAGE_TEMPLATE_FILES = List.of(
            "project-overview.template.json",
            "asset-overview.template.json"
    );

    private final LowCodeFormService lowCodeFormService;
    private final LowCodeListService lowCodeListService;
    private final LowCodeTabService lowCodeTabService;
    private final LowCodeRelatedPageService lowCodeRelatedPageService;

    @Override
    public void run(ApplicationArguments args) {
        initFormTemplates();
        initListTemplates();
        initTabTemplates();
        initRelatedPageTemplates();
    }

    /**
     * 初始化表单模板：当 {@code pms_lowcode_form} 表为空时加载预置模板。
     */
    private void initFormTemplates() {
        try {
            long existing = lowCodeFormService.count(new LambdaQueryWrapper<>());
            if (existing > 0) {
                log.info("[LowCode] 已存在 {} 条表单配置，跳过表单模板初始化", existing);
                return;
            }
            log.info("[LowCode] 表单配置为空，开始加载预置表单模板...");
            int success = 0;
            for (String file : FORM_TEMPLATE_FILES) {
                if (loadFormTemplate(file)) {
                    success++;
                }
            }
            log.info("[LowCode] 预置表单模板加载完成，成功导入 {} 个", success);
        } catch (Exception e) {
            // 模板初始化失败不影响应用启动
            log.error("[LowCode] 表单模板初始化失败", e);
        }
    }

    /**
     * 初始化列表模板：当 {@code pms_lowcode_list} 表为空时加载预置模板。
     */
    private void initListTemplates() {
        try {
            long existing = lowCodeListService.count(new LambdaQueryWrapper<>());
            if (existing > 0) {
                log.info("[LowCode] 已存在 {} 条列表配置，跳过列表模板初始化", existing);
                return;
            }
            log.info("[LowCode] 列表配置为空，开始加载预置列表模板...");
            int success = 0;
            for (String file : LIST_TEMPLATE_FILES) {
                if (loadListTemplate(file)) {
                    success++;
                }
            }
            log.info("[LowCode] 预置列表模板加载完成，成功导入 {} 个", success);
        } catch (Exception e) {
            // 模板初始化失败不影响应用启动
            log.error("[LowCode] 列表模板初始化失败", e);
        }
    }

    /**
     * 初始化标签页模板：当 {@code pms_lowcode_tab} 表为空时加载预置模板。
     */
    private void initTabTemplates() {
        try {
            long existing = lowCodeTabService.count(new LambdaQueryWrapper<>());
            if (existing > 0) {
                log.info("[LowCode] 已存在 {} 条标签页配置，跳过标签页模板初始化", existing);
                return;
            }
            log.info("[LowCode] 标签页配置为空，开始加载预置标签页模板...");
            int success = 0;
            for (String file : TAB_TEMPLATE_FILES) {
                if (loadTabTemplate(file)) {
                    success++;
                }
            }
            log.info("[LowCode] 预置标签页模板加载完成，成功导入 {} 个", success);
        } catch (Exception e) {
            // 模板初始化失败不影响应用启动
            log.error("[LowCode] 标签页模板初始化失败", e);
        }
    }

    /**
     * 初始化关联页模板：当 {@code pms_lowcode_related_page} 表为空时加载预置模板。
     */
    private void initRelatedPageTemplates() {
        try {
            long existing = lowCodeRelatedPageService.count(new LambdaQueryWrapper<>());
            if (existing > 0) {
                log.info("[LowCode] 已存在 {} 条关联页配置，跳过关联页模板初始化", existing);
                return;
            }
            log.info("[LowCode] 关联页配置为空，开始加载预置关联页模板...");
            int success = 0;
            for (String file : RELATED_PAGE_TEMPLATE_FILES) {
                if (loadRelatedPageTemplate(file)) {
                    success++;
                }
            }
            log.info("[LowCode] 预置关联页模板加载完成，成功导入 {} 个", success);
        } catch (Exception e) {
            // 模板初始化失败不影响应用启动
            log.error("[LowCode] 关联页模板初始化失败", e);
        }
    }

    /**
     * 加载单个表单模板文件并导入。
     *
     * @param fileName 模板文件名（相对 {@link #FORM_TEMPLATE_DIR}）
     * @return 是否导入成功
     */
    private boolean loadFormTemplate(String fileName) {
        String path = FORM_TEMPLATE_DIR + fileName;
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[LowCode] 表单模板文件不存在: {}", path);
            return false;
        }
        try {
            String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            LowCodeForm imported = lowCodeFormService.importConfig(json);
            log.info("[LowCode] 表单模板导入成功: {} -> code={}", fileName, imported.getCode());
            return true;
        } catch (Exception e) {
            log.error("[LowCode] 表单模板导入失败: {}", fileName, e);
            return false;
        }
    }

    /**
     * 加载单个列表模板文件并导入。
     *
     * @param fileName 模板文件名（相对 {@link #LIST_TEMPLATE_DIR}）
     * @return 是否导入成功
     */
    private boolean loadListTemplate(String fileName) {
        String path = LIST_TEMPLATE_DIR + fileName;
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[LowCode] 列表模板文件不存在: {}", path);
            return false;
        }
        try {
            String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            LowCodeList imported = lowCodeListService.importConfig(json);
            log.info("[LowCode] 列表模板导入成功: {} -> code={}", fileName, imported.getCode());
            return true;
        } catch (Exception e) {
            log.error("[LowCode] 列表模板导入失败: {}", fileName, e);
            return false;
        }
    }

    /**
     * 加载单个标签页模板文件并导入。
     *
     * @param fileName 模板文件名（相对 {@link #TAB_TEMPLATE_DIR}）
     * @return 是否导入成功
     */
    private boolean loadTabTemplate(String fileName) {
        String path = TAB_TEMPLATE_DIR + fileName;
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[LowCode] 标签页模板文件不存在: {}", path);
            return false;
        }
        try {
            String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            LowCodeTab imported = lowCodeTabService.importConfig(json);
            log.info("[LowCode] 标签页模板导入成功: {} -> code={}", fileName, imported.getCode());
            return true;
        } catch (Exception e) {
            log.error("[LowCode] 标签页模板导入失败: {}", fileName, e);
            return false;
        }
    }

    /**
     * 加载单个关联页模板文件并导入。
     *
     * @param fileName 模板文件名（相对 {@link #RELATED_PAGE_TEMPLATE_DIR}）
     * @return 是否导入成功
     */
    private boolean loadRelatedPageTemplate(String fileName) {
        String path = RELATED_PAGE_TEMPLATE_DIR + fileName;
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[LowCode] 关联页模板文件不存在: {}", path);
            return false;
        }
        try {
            String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            LowCodeRelatedPage imported = lowCodeRelatedPageService.importConfig(json);
            log.info("[LowCode] 关联页模板导入成功: {} -> code={}", fileName, imported.getCode());
            return true;
        } catch (Exception e) {
            log.error("[LowCode] 关联页模板导入失败: {}", fileName, e);
            return false;
        }
    }
}
