<script setup lang="ts">
/**
 * 低代码页面通用渲染入口。
 *
 * <p>路由 /lowcode/:pageType/:pageCode 进入此组件。组件职责：</p>
 * <ul>
 *   <li>根据 pageType 调用对应 API 拉取已发布配置（getFormByCode 等）</li>
 *   <li>调用权限校验接口 checkLowCodePermission，无权限时显示 403 提示</li>
 *   <li>解析配置 JSON 字符串后，动态加载对应渲染器渲染</li>
 *   <li>加载中显示骨架屏；配置不存在显示 404；无权限显示 403</li>
 *   <li>将路由 query 参数透传给渲染器作为上下文数据</li>
 * </ul>
 *
 * <p>4 种渲染器对应关系：</p>
 * <ul>
 *   <li>form → LowCodeFormRenderer（props: config: FormConfig）</li>
 *   <li>list → LowCodeListRenderer（props: config: ListConfig）</li>
 *   <li>tab → LowCodeTabRenderer（props: config: TabConfig, contextData）</li>
 *   <li>related-page → LowCodeRelatedPageRenderer（props: config: RelatedPageConfig, contextData）</li>
 * </ul>
 */
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import {
  checkLowCodePermission,
  getFormByCode,
  getListByCode,
  getRelatedPageByCode,
  getTabByCode,
  type LowCodeFormConfig,
  type LowCodeListConfig,
  type LowCodePageType,
  type LowCodeRelatedPageConfig,
  type LowCodeTabConfig
} from '@/api/lowcode'
import { TOKEN_KEY } from '@/utils/request'

const route = useRoute()
const router = useRouter()

/** 加载状态：loading / done / not-found / forbidden / error */
const state = ref<'loading' | 'done' | 'not-found' | 'forbidden' | 'error'>('loading')

/** 解析后的配置对象（FormConfig / ListConfig / TabConfig / RelatedPageConfig 之一） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const config = ref<any>(null)
const pageName = ref('')
const formDataModel = ref<Record<string, unknown>>({})

const pageType = computed(() => route.params.pageType as LowCodePageType)
const pageCode = computed(() => route.params.pageCode as string)
const formMode = computed(() => (route.query.mode as string) || 'view')
const entityCode = computed(() => {
  const code = pageCode.value
  if (code.startsWith('form_')) return code.substring(5)
  if (code.startsWith('list_')) return code.substring(5)
  return code
})
const isFormReadOnly = computed(() => pageType.value === 'form' && formMode.value === 'view')

/** 合法的页面类型集合 */
const VALID_PAGE_TYPES: ReadonlySet<string> = new Set(['form', 'list', 'tab', 'related-page'])

/** 根据 pageType 解析渲染器组件（异步加载，避免首屏加载全部渲染器） */
const renderer = computed(() => {
  switch (pageType.value) {
    case 'form':
      return defineAsyncComponent(() => import('@/components/LowCodeFormRenderer/index.vue'))
    case 'list':
      return defineAsyncComponent(() => import('@/components/LowCodeListRenderer/index.vue'))
    case 'tab':
      return defineAsyncComponent(() => import('@/components/LowCodeTabRenderer/index.vue'))
    case 'related-page':
      return defineAsyncComponent(() => import('@/components/LowCodeRelatedPageRenderer/index.vue'))
    default:
      return null
  }
})

/** 透传给渲染器的上下文数据（route / params / query） */
const contextData = computed(() => ({
  route,
  params: route.params,
  query: route.query
}))

/**
 * 拉取已发布配置并解析 JSON。
 * 返回 { name, config } 或 null（配置不存在 / 状态非 PUBLISHED）。
 */
async function fetchConfig(
  type: LowCodePageType,
  code: string
): Promise<{ name: string; config: unknown } | null> {
  let meta:
    | (LowCodeFormConfig & { formConfig?: string })
    | (LowCodeListConfig & { listConfig?: string })
    | (LowCodeTabConfig & { tabConfig?: string })
    | (LowCodeRelatedPageConfig & { relatedConfig?: string })
    | null = null
  let configStr: string | undefined
  switch (type) {
    case 'form':
      meta = await getFormByCode(code)
      configStr = (meta as LowCodeFormConfig | null)?.formConfig
      break
    case 'list':
      meta = await getListByCode(code)
      configStr = (meta as LowCodeListConfig | null)?.listConfig
      break
    case 'tab':
      meta = await getTabByCode(code)
      configStr = (meta as LowCodeTabConfig | null)?.tabConfig
      break
    case 'related-page':
      meta = await getRelatedPageByCode(code)
      configStr = (meta as LowCodeRelatedPageConfig | null)?.relatedConfig
      break
    default:
      return null
  }
  if (!meta) {
    return null
  }
  if (!configStr) {
    return null
  }
  try {
    const parsed = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    return { name: meta.name || '', config: parsed }
  } catch (e) {
    console.error('低代码配置 JSON 解析失败', e)
    return null
  }
}

/** 加载并校验低代码页面 */
async function load() {
  state.value = 'loading'
  config.value = null

  // 1. 校验 pageType 合法性
  if (!VALID_PAGE_TYPES.has(pageType.value)) {
    state.value = 'not-found'
    return
  }
  if (!pageCode.value) {
    state.value = 'not-found'
    return
  }

  try {
    // 2. 权限校验（调用后端 /api/lowcode/permission/check）
    //    后端返回 false 时显示 403；接口异常时降级为放行（由配置接口本身的鉴权兜底）
    let allowed = true
    try {
      allowed = await checkLowCodePermission(pageType.value, pageCode.value)
    } catch (e) {
      // 权限校验接口不可用时降级放行，避免阻断已发布的低代码页面访问
      console.warn('低代码权限校验接口不可用，降级放行', e)
      allowed = true
    }
    if (!allowed) {
      state.value = 'forbidden'
      ElMessage.error('您没有访问该低代码页面的权限')
      return
    }

    // 3. 拉取已发布配置
    const result = await fetchConfig(pageType.value, pageCode.value)
    if (!result) {
      state.value = 'not-found'
      return
    }
    config.value = result.config
    pageName.value = result.name || ''
    // 更新浏览器标题
    if (result.name) {
      document.title = `${result.name} - 网络设备工程项目管理系统`
    }
    formDataModel.value = {}
    if (pageType.value === 'form' && (formMode.value === 'view' || formMode.value === 'edit')) {
      const id = route.query.id as string
      if (id) {
        const token = localStorage.getItem(TOKEN_KEY) || ''
        const response = await axios.get(`/api/lowcode/data/${entityCode.value}/${id}`, {
          headers: { Authorization: `Bearer ${token}` }
        })
        formDataModel.value = response.data?.data || response.data || {}
      }
    }
    state.value = 'done'
  } catch (e) {
    console.error('加载低代码页面失败', e)
    state.value = 'error'
  }
}

onMounted(load)

// 路由参数变化时重新加载（同一组件实例复用场景）
watch([pageType, pageCode], () => {
  load()
})

function goBack() {
  router.back()
}

async function handleFormSubmit() {
  try {
    const formData: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(formDataModel.value)) {
      formData[key] = value === '' || value === undefined ? null : value
    }
    const token = localStorage.getItem(TOKEN_KEY) || ''
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    const baseUrl = `/api/lowcode/data/${entityCode.value}`
    const id = route.query.id as string
    if (formMode.value === 'edit' && id) {
      await axios.put(`${baseUrl}/${id}`, formData, { headers })
    } else {
      await axios.post(baseUrl, formData, { headers })
    }
    ElMessage.success('保存成功')
    router.back()
  } catch (e) {
    console.error('表单提交失败', e)
    ElMessage.error('保存失败，请检查数据')
  }
}
</script>

<template>
  <div class="page-container">
    <!-- 加载中：骨架屏 -->
    <el-card v-if="state === 'loading'" shadow="never">
      <el-skeleton :rows="10" animated />
    </el-card>

    <!-- 404：配置不存在 -->
    <el-card v-else-if="state === 'not-found'" shadow="never">
      <el-result icon="warning" title="页面不存在" sub-title="请检查页面编码或联系管理员">
        <template #extra><el-button type="primary" @click="goBack">返回</el-button></template>
      </el-result>
    </el-card>

    <!-- 403：无权限 -->
    <el-card v-else-if="state === 'forbidden'" shadow="never">
      <el-result icon="error" title="无访问权限" sub-title="您没有访问该低代码页面的权限，请联系管理员授权">
        <template #extra><el-button type="primary" @click="goBack">返回</el-button></template>
      </el-result>
    </el-card>

    <!-- 加载异常 -->
    <el-card v-else-if="state === 'error'" shadow="never">
      <el-result icon="error" title="加载失败" sub-title="低代码页面加载异常，请稍后重试">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
          <el-button @click="goBack">返回</el-button>
        </template>
      </el-result>
    </el-card>

    <el-card v-else-if="state === 'done' && renderer && config" shadow="never">
      <template #header><span class="page-title">{{ pageName || '低代码页面' }}</span></template>
      <component
        :is="renderer"
        v-model="formDataModel"
        :config="config"
        :context-data="contextData"
        :auto-fetch="true"
        :disabled="isFormReadOnly"
      />
      <div v-if="pageType === 'form'" class="form-actions">
        <template v-if="!isFormReadOnly">
          <el-button type="primary" @click="handleFormSubmit">保存</el-button>
          <el-button @click="goBack">取消</el-button>
        </template>
        <el-button v-else type="primary" @click="goBack">返回</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.form-actions {
  margin-top: 20px;
  text-align: center;
}
</style>
