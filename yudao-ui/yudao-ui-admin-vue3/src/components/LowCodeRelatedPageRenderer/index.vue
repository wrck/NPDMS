<script setup lang="ts">
/**
 * 低代码关联页渲染引擎。
 *
 * <p>根据传入的 {@link RelatedPageConfig} 动态渲染关联区块，支持：</p>
 * <ul>
 *   <li>3 种布局：grid（el-row + el-col）/ tabs（el-tabs）/ collapse（el-collapse）</li>
 *   <li>4 种区块类型：form（LowCodeFormRenderer）/ list（LowCodeListRenderer）/
 *       tab（LowCodeTabRenderer）/ custom（iframe 或 router-view）</li>
 *   <li>section.span 栅格宽度（grid 模式生效）</li>
 *   <li>section.order 排序（升序排列区块）</li>
 *   <li>section.visible 显示条件表达式（对 contextData 求值）</li>
 *   <li>section.props 模板变量解析（${route.params.id} / ${row.field} 等）</li>
 * </ul>
 *
 * <p>渲染 form/list/tab 时通过异步加载对应的低代码配置（getFormByCode /
 * getListByCode / getTabByCode），并嵌入对应的渲染器组件。
 * custom 类型直接渲染 iframe（pageUrl）或 emit navigate 事件由业务层处理。</p>
 *
 * <p>对外暴露 section-change / navigate / page-loaded 事件，方便业务层介入。</p>
 */
import { computed, ref, watch, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  SectionType,
  RelatedPageLayout,
  getFormByCode,
  getListByCode,
  getTabByCode,
  listForms,
  listLists,
  listTabs,
  normalizeRelatedPageConfig,
  type FormConfig,
  type ListConfig,
  type LowCodeFormConfig,
  type LowCodeListConfig,
  type LowCodeTabConfig,
  type TabConfig,
  type RelatedPageConfig,
  type RelatedPageSectionConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import { useUserStore } from '@/stores/user'

/** 异步加载子页面渲染器，避免循环依赖 */
const LowCodeFormRenderer = defineAsyncComponent(() => import('@/components/LowCodeFormRenderer/index.vue'))
const LowCodeListRenderer = defineAsyncComponent(() => import('@/components/LowCodeListRenderer/index.vue'))
const LowCodeTabRenderer = defineAsyncComponent(() => import('@/components/LowCodeTabRenderer/index.vue'))

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** 关联页配置（解析后的 RelatedPageConfig 对象） */
    config: RelatedPageConfig
    /** 上下文数据（用于 props 解析与 visible 表达式求值） */
    contextData?: Record<string, unknown>
    /** 设计器预览使用的子页面配置（pageCode → config） */
    previewConfigs?: Record<string, unknown>
    /** 设计器预览时允许引用尚未发布的草稿配置 */
    allowDraft?: boolean
  }>(),
  {
    contextData: () => ({}),
    previewConfigs: () => ({}),
    allowDraft: false
  }
)

/** Emits 定义 */
const emit = defineEmits<{
  (e: 'section-change', section: RelatedPageSectionConfig): void
  (e: 'navigate', url: string, section: RelatedPageSectionConfig): void
  (e: 'page-loaded', section: RelatedPageSectionConfig, pageConfig: unknown): void
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/**
 * 排序+过滤后的区块列表：按 order 升序，相同 order 按数组顺序；过滤 visible=false 的区块。
 */
const visibleSections = computed<RelatedPageSectionConfig[]>(() => {
  const list = normalizeRelatedPageConfig(props.config).sections
    .filter((s) => evalVisible(s))
    .slice()
  list.sort((a, b) => {
    const oa = a.order ?? 100
    const ob = b.order ?? 100
    return oa - ob
  })
  return list
})

/** 布局类型（默认 grid） */
const layout = computed(() => normalizeRelatedPageConfig(props.config).layout || RelatedPageLayout.GRID)

/** 栅格间距（默认 16） */
const gutter = computed(() => normalizeRelatedPageConfig(props.config).gutter ?? 16)

/**
 * 解析 section.span 为 el-col 绑定属性。
 *
 * <p>向后兼容：span 为数字或缺省时按 :span= 渲染（缺省 24）；
 * span 为响应式断点对象时按 :xs= :sm= :md= :lg= :xl= 渲染。</p>
 */
function colProps(span: number | ResponsiveSpan | undefined): Record<string, number> {
  if (span === undefined || typeof span === 'number') {
    return { span: span ?? 24 }
  }
  const result: Record<string, number> = {}
  if (span.xs !== undefined) result.xs = span.xs
  if (span.sm !== undefined) result.sm = span.sm
  if (span.md !== undefined) result.md = span.md
  if (span.lg !== undefined) result.lg = span.lg
  if (span.xl !== undefined) result.xl = span.xl
  return result
}

/** tabs/collapse 模式下当前激活项 */
const activeTab = ref<string>('')
const activeCollapse = ref<string[]>([])

// 初始化 tabs/collapse 激活第一项
watch(
  visibleSections,
  (sections) => {
    if (sections.length === 0) return
    if (!activeTab.value && sections[0]) {
      activeTab.value = sections[0].id
    }
    activeCollapse.value = sections.map((s) => s.id)
  },
  { immediate: true }
)

/**
 * 求值 visible 显示条件表达式。
 */
function evalVisible(section: RelatedPageSectionConfig): boolean {
  const expr = section.visible
  if (!expr || !expr.trim()) return true
  try {
    const row = (props.contextData.row as Record<string, unknown>) || {}
    const context = (props.contextData.context as Record<string, unknown>) || {}
    // eslint-disable-next-line no-new-func
    const fn = new Function('row', 'context', 'route', 'user', `"use strict"; return (${expr});`)
    const result = fn(row, context, route, userStore.userInfo || {})
    return !!result
  } catch (e) {
    console.warn(`[LowCodeRelatedPageRenderer] visible 表达式求值失败: ${expr}`, e)
    return true
  }
}

/**
 * 解析 props 模板变量。
 */
function resolveProps(section: RelatedPageSectionConfig): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  const src = section.props || {}
  const row = (props.contextData.row as Record<string, unknown>) || {}
  const context = (props.contextData.context as Record<string, unknown>) || {}
  const user = (userStore.userInfo as Record<string, unknown>) || {}
  for (const key of Object.keys(src)) {
    const val = src[key]
    if (typeof val === 'string') {
      result[key] = resolveTemplate(val, { row, context, route, user })
    } else {
      result[key] = val
    }
  }
  return result
}

/** 模板变量正则：匹配 ${...} */
const TEMPLATE_RE = /\$\{\s*([^}]+?)\s*\}/g

/** 解析单个字符串中的模板变量 */
function resolveTemplate(tpl: string, ctx: Record<string, unknown>): string {
  return tpl.replace(TEMPLATE_RE, (_, expr: string) => {
    try {
      // eslint-disable-next-line no-new-func
      const fn = new Function(...Object.keys(ctx), `"use strict"; return (${expr});`)
      const val = fn(...Object.values(ctx))
      return val == null ? '' : String(val)
    } catch (e) {
      console.warn(`[LowCodeRelatedPageRenderer] props 模板解析失败: ${expr}`, e)
      return ''
    }
  })
}

// ===================== 子页面配置加载 =====================

/** 已加载的子页面配置缓存：key 为 section.id */
const pageConfigCache = ref<Record<string, unknown>>({})

/** 各 section 加载状态 */
const loadingMap = ref<Record<string, boolean>>({})

/** 各 section 加载错误信息 */
const errorMap = ref<Record<string, string>>({})

/**
 * 加载指定 section 引用的子页面配置。
 */
async function loadPageConfig(section: RelatedPageSectionConfig): Promise<void> {
  if (!section.pageCode) {
    return
  }
  if (pageConfigCache.value[section.pageCode]) {
    return
  }
  if (props.previewConfigs[section.pageCode]) {
    pageConfigCache.value = { ...pageConfigCache.value, [section.pageCode]: props.previewConfigs[section.pageCode] }
    emit('page-loaded', section, props.previewConfigs[section.pageCode])
    return
  }
  loadingMap.value[section.id] = true
  errorMap.value[section.id] = ''
  try {
    let cfg: unknown = null
    if (section.type === SectionType.FORM) {
      let res: LowCodeFormConfig | null = await getFormByCode(section.pageCode)
      if (!res && props.allowDraft) {
        const page = await listForms({ page: 1, size: 1, code: section.pageCode })
        res = page.records.find((item) => item.code === section.pageCode) ?? null
      }
      const raw = res?.formConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as FormConfig
    } else if (section.type === SectionType.LIST) {
      let res: LowCodeListConfig | null = await getListByCode(section.pageCode)
      if (!res && props.allowDraft) {
        const page = await listLists({ page: 1, size: 1, code: section.pageCode })
        res = page.records.find((item) => item.code === section.pageCode) ?? null
      }
      const raw = res?.listConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as ListConfig
    } else if (section.type === SectionType.TAB) {
      let res: LowCodeTabConfig | null = await getTabByCode(section.pageCode)
      if (!res && props.allowDraft) {
        const page = await listTabs({ page: 1, size: 1, code: section.pageCode })
        res = page.records.find((item) => item.code === section.pageCode) ?? null
      }
      const raw = res?.tabConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as TabConfig
    }
    if (cfg) {
      pageConfigCache.value = { ...pageConfigCache.value, [section.pageCode]: cfg }
      emit('page-loaded', section, cfg)
    } else {
      errorMap.value[section.id] = '引用的页面配置为空或不存在'
    }
  } catch (e) {
    errorMap.value[section.id] = (e as Error).message || '加载失败'
    console.warn(`[LowCodeRelatedPageRenderer] 加载子页面配置失败: ${section.pageCode}`, e)
  } finally {
    loadingMap.value[section.id] = false
  }
}

/** 可见区块变化时主动加载所有区块配置 */
watch(
  visibleSections,
  (sections) => {
    for (const section of sections) {
      loadPageConfig(section)
    }
  },
  { immediate: true }
)

/** tabs 模式切换 */
function handleTabChange(tabId: string) {
  const section = visibleSections.value.find((s) => s.id === tabId)
  if (section) {
    emit('section-change', section)
  }
}

/** collapse 模式切换 */
function handleCollapseChange(activeNames: string | string[]) {
  const arr = Array.isArray(activeNames) ? activeNames : [activeNames]
  if (arr.length > 0) {
    const section = visibleSections.value.find((s) => s.id === arr[arr.length - 1])
    if (section) {
      emit('section-change', section)
    }
  }
}

/** custom 类型 section 跳转 */
function handleCustomNavigate(section: RelatedPageSectionConfig) {
  if (!section.pageUrl) {
    ElMessage.warning('自定义页面未配置 pageUrl')
    return
  }
  const resolved = resolveTemplate(section.pageUrl, {
    row: (props.contextData.row as Record<string, unknown>) || {},
    context: (props.contextData.context as Record<string, unknown>) || {},
    route,
    user: (userStore.userInfo as Record<string, unknown>) || {}
  })
  emit('navigate', resolved, section)
  if (resolved.startsWith('http')) {
    window.open(resolved, '_blank')
  } else {
    router.push(resolved).catch(() => {
      window.open(resolved, '_blank')
    })
  }
}
</script>

<template>
  <div class="low-code-related-page-renderer">
    <!-- ============ Grid 布局（默认） ============ -->
    <el-row v-if="layout === 'grid'" :gutter="gutter">
      <el-col
        v-for="section in visibleSections"
        :key="section.id"
        v-bind="colProps(section.span)"
      >
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">{{ section.title }}</span>
          </template>
          <div class="section-content" v-loading="loadingMap[section.id]">
            <div v-if="errorMap[section.id]" class="section-error">
              <el-alert :title="`加载失败：${errorMap[section.id]}`" type="error" :closable="false" />
            </div>

            <!-- 表单 -->
            <LowCodeFormRenderer
              v-else-if="section.type === 'form' && pageConfigCache[section.pageCode ?? '']"
              :config="pageConfigCache[section.pageCode ?? ''] as FormConfig"
              :model-value="resolveProps(section)"
            />

            <!-- 列表 -->
            <LowCodeListRenderer
              v-else-if="section.type === 'list' && pageConfigCache[section.pageCode ?? '']"
              :config="pageConfigCache[section.pageCode ?? ''] as ListConfig"
              :auto-fetch="true"
            />

            <!-- 标签页 -->
            <LowCodeTabRenderer
              v-else-if="section.type === 'tab' && pageConfigCache[section.pageCode ?? '']"
              :config="pageConfigCache[section.pageCode ?? ''] as TabConfig"
              :allow-draft="allowDraft"
              :context-data="resolveProps(section)"
            />

            <!-- 自定义 -->
            <div v-else-if="section.type === 'custom'" class="custom-section">
              <iframe
                v-if="section.pageUrl && section.pageUrl.startsWith('http')"
                :src="resolveTemplate(section.pageUrl, {
                  row: (contextData.row as Record<string, unknown>) || {},
                  context: (contextData.context as Record<string, unknown>) || {},
                  route,
                  user: (userStore.userInfo as Record<string, unknown>) || {}
                })"
                frameborder="0"
                class="custom-iframe"
              />
              <div v-else-if="section.pageUrl" class="custom-router">
                <el-button type="primary" @click="handleCustomNavigate(section)">打开页面</el-button>
                <span class="custom-router-tip">{{ section.pageUrl }}</span>
              </div>
              <el-empty v-else description="自定义页面未配置 URL" :image-size="80" />
            </div>

            <el-empty
              v-else-if="section.type !== 'custom'"
              description="配置加载中..."
              :image-size="80"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ Tabs 布局 ============ -->
    <el-tabs v-else-if="layout === 'tabs'" v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <el-tab-pane
        v-for="section in visibleSections"
        :key="section.id"
        :label="section.title"
        :name="section.id"
      >
        <div class="section-content" v-loading="loadingMap[section.id]">
          <div v-if="errorMap[section.id]" class="section-error">
            <el-alert :title="`加载失败：${errorMap[section.id]}`" type="error" :closable="false" />
          </div>
          <LowCodeFormRenderer
            v-else-if="section.type === 'form' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as FormConfig"
            :model-value="resolveProps(section)"
          />
          <LowCodeListRenderer
            v-else-if="section.type === 'list' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as ListConfig"
            :auto-fetch="true"
          />
          <LowCodeTabRenderer
            v-else-if="section.type === 'tab' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as TabConfig"
            :allow-draft="allowDraft"
            :context-data="resolveProps(section)"
          />
          <div v-else-if="section.type === 'custom'" class="custom-section">
            <iframe
              v-if="section.pageUrl && section.pageUrl.startsWith('http')"
              :src="resolveTemplate(section.pageUrl, {
                row: (contextData.row as Record<string, unknown>) || {},
                context: (contextData.context as Record<string, unknown>) || {},
                route,
                user: (userStore.userInfo as Record<string, unknown>) || {}
              })"
              frameborder="0"
              class="custom-iframe"
            />
            <div v-else-if="section.pageUrl" class="custom-router">
              <el-button type="primary" @click="handleCustomNavigate(section)">打开页面</el-button>
              <span class="custom-router-tip">{{ section.pageUrl }}</span>
            </div>
            <el-empty v-else description="自定义页面未配置 URL" :image-size="80" />
          </div>
          <el-empty v-else description="配置加载中..." :image-size="80" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ Collapse 布局 ============ -->
    <el-collapse v-else-if="layout === 'collapse'" v-model="activeCollapse" @change="handleCollapseChange">
      <el-collapse-item
        v-for="section in visibleSections"
        :key="section.id"
        :title="section.title"
        :name="section.id"
      >
        <div class="section-content" v-loading="loadingMap[section.id]">
          <div v-if="errorMap[section.id]" class="section-error">
            <el-alert :title="`加载失败：${errorMap[section.id]}`" type="error" :closable="false" />
          </div>
          <LowCodeFormRenderer
            v-else-if="section.type === 'form' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as FormConfig"
            :model-value="resolveProps(section)"
          />
          <LowCodeListRenderer
            v-else-if="section.type === 'list' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as ListConfig"
            :auto-fetch="true"
          />
          <LowCodeTabRenderer
            v-else-if="section.type === 'tab' && pageConfigCache[section.pageCode ?? '']"
            :config="pageConfigCache[section.pageCode ?? ''] as TabConfig"
            :allow-draft="allowDraft"
            :context-data="resolveProps(section)"
          />
          <div v-else-if="section.type === 'custom'" class="custom-section">
            <iframe
              v-if="section.pageUrl && section.pageUrl.startsWith('http')"
              :src="resolveTemplate(section.pageUrl, {
                row: (contextData.row as Record<string, unknown>) || {},
                context: (contextData.context as Record<string, unknown>) || {},
                route,
                user: (userStore.userInfo as Record<string, unknown>) || {}
              })"
              frameborder="0"
              class="custom-iframe"
            />
            <div v-else-if="section.pageUrl" class="custom-router">
              <el-button type="primary" @click="handleCustomNavigate(section)">打开页面</el-button>
              <span class="custom-router-tip">{{ section.pageUrl }}</span>
            </div>
            <el-empty v-else description="自定义页面未配置 URL" :image-size="80" />
          </div>
          <el-empty v-else description="配置加载中..." :image-size="80" />
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 未知布局 -->
    <el-empty v-else :description="`未知的布局类型: ${layout}`" :image-size="80" />
  </div>
</template>

<style scoped>
.low-code-related-page-renderer {
  width: 100%;
}

.section-card {
  margin-bottom: 12px;
}

.section-title {
  font-weight: 600;
  font-size: 14px;
}

.section-content {
  min-height: 120px;
}

.section-error {
  padding: 8px 0;
}

.custom-section {
  width: 100%;
}

.custom-iframe {
  width: 100%;
  min-height: 600px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.custom-router {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.custom-router-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-family: monospace;
}
</style>
