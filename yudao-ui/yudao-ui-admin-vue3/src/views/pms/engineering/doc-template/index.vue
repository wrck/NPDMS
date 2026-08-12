<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="模板编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="文档类别" prop="docCategory">
        <el-select v-model="query.docCategory" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DOC_CATEGORY)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_DOC_TEMPLATE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-doc-template:create']">
          <Icon icon="ep:plus" />新增模板
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无文档模板数据">
      <el-table-column prop="code" label="模板编号" width="160" />
      <el-table-column prop="name" label="模板名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="docCategory" label="文档类别" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DOC_CATEGORY" :value="row.docCategory" />
        </template>
      </el-table-column>
      <el-table-column prop="parentTemplateId" label="父模板" width="160">
        <template #default="{ row }">
          <span>{{ parentTemplateName(row.parentTemplateId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="适用条件" width="240">
        <template #default="{ row }">
          <div v-if="parseApplicability(row.applicability).length">
            <el-tag
              v-for="item in parseApplicability(row.applicability)"
              :key="item.key"
              size="small"
              class="mr-3px mb-3px"
            >
              {{ item.label }}
            </el-tag>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="currentVersionId" label="当前版本" width="120">
        <template #default="{ row }">
          <span>{{ currentVersionLabel(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DOC_TEMPLATE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-doc-template:query']">
            详情
          </el-button>
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-doc-template:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handlePublish(row)"
            v-hasPermi="['pms:eng-doc-template:publish']"
          >
            发布
          </el-button>
          <el-button
            link
            type="info"
            v-if="row.status === 1"
            @click="handleDisable(row)"
            v-hasPermi="['pms:eng-doc-template:publish']"
          >
            停用
          </el-button>
          <el-button
            link
            type="primary"
            @click="openVersionDialog(row)"
            v-hasPermi="['pms:eng-doc-template:update']"
          >
            版本管理
          </el-button>
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-doc-template:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑文档模板' : '新建文档模板'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="模板编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 DT-REQ-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文档类别" prop="docCategory">
            <el-select v-model="form.docCategory" class="!w-full" @change="onDocCategoryChange">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DOC_CATEGORY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="父模板" prop="parentTemplateId">
            <el-select v-model="form.parentTemplateId" clearable class="!w-full" placeholder="可选择同类别的基础模板">
              <el-option
                v-for="item in parentTemplateOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="适用条件">
            <div class="vis-applicability">
              <el-row :gutter="12" class="mb-10px">
                <el-col :span="6">
                  <el-form-item label="项目类型" label-width="80px">
                    <el-select v-model="applicabilityObj.projectType" multiple collapse-tags clearable placeholder="通配" class="!w-full">
                      <el-option v-for="item in optionMap.projectType" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="网络类型" label-width="80px">
                    <el-select v-model="applicabilityObj.networkType" multiple collapse-tags clearable placeholder="通配" class="!w-full">
                      <el-option v-for="item in optionMap.networkType" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="产品类型" label-width="80px">
                    <el-select v-model="applicabilityObj.productType" multiple collapse-tags clearable placeholder="通配" class="!w-full">
                      <el-option v-for="item in optionMap.productType" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="实施方式" label-width="80px">
                    <el-select v-model="applicabilityObj.implementMode" multiple collapse-tags clearable placeholder="通配" class="!w-full">
                      <el-option v-for="item in optionMap.implementMode" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="6">
                  <el-form-item label="优先级" label-width="80px">
                    <el-input-number v-model="applicabilityObj.priority" :min="0" controls-position="right" class="!w-full" />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="默认模板" label-width="80px">
                    <el-switch v-model="applicabilityObj.isDefault" active-text="是" inactive-text="否" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <span class="vis-hint">各维度留空表示通配；优先级数值越大越优先；默认模板用于无匹配时兜底</span>
                </el-col>
              </el-row>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="模板说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 版本管理对话框 -->
  <Dialog v-model="versionVisible" title="版本管理" width="960px">
    <div class="mb-10px">
      <el-button type="primary" @click="openVersionCreate" v-hasPermi="['pms:eng-doc-template:create']">
        <Icon icon="ep:plus" />新增版本
      </el-button>
    </div>
    <el-table v-loading="versionLoading" :data="versionRows" empty-text="暂无版本数据">
      <el-table-column prop="versionLabel" label="版本号" width="160" />
      <el-table-column prop="published" label="发布状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.published === 1 ? 'success' : 'info'">
            {{ row.published === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeLog" label="变更说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="success"
            v-if="row.published !== 1"
            @click="handlePublishVersion(row)"
            v-hasPermi="['pms:eng-doc-template:publish']"
          >
            发布版本
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增版本表单 -->
    <Dialog v-model="versionCreateVisible" title="新增版本" width="800px" append-to-body>
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="140px">
        <el-form-item label="版本号" prop="versionLabel">
          <el-input v-model="versionForm.versionLabel" placeholder="如 v1.0" />
        </el-form-item>
        <el-form-item label="章节定义" prop="sections">
          <div class="vis-sections">
            <div v-if="!sectionsArr.length" class="vis-empty">暂无章节，点击下方按钮添加</div>
            <div v-for="(section, idx) in sectionsArr" :key="idx" class="vis-section-item">
              <div class="vis-section-header">
                <el-input v-model="section.code" placeholder="章节编码（如 background）" class="!w-200px" />
                <el-input v-model="section.title" placeholder="章节标题" class="!w-220px" />
                <el-form-item label="序号" label-width="50px" class="!mb-0">
                  <el-input-number v-model="section.order" :min="1" controls-position="right" class="!w-100px" />
                </el-form-item>
                <el-button link :disabled="idx === 0" @click="moveSection(idx, -1)"><Icon icon="ep:top" /></el-button>
                <el-button link :disabled="idx === sectionsArr.length - 1" @click="moveSection(idx, 1)"><Icon icon="ep:bottom" /></el-button>
                <el-button link type="danger" @click="removeSection(idx)"><Icon icon="ep:delete" />删除章节</el-button>
              </div>
              <div class="vis-fields">
                <div v-for="(field, fidx) in section.fields" :key="fidx" class="vis-field-item">
                  <el-select v-model="field.controlType" class="!w-130px" placeholder="控件类型">
                    <el-option v-for="ct in controlTypeOptions" :key="ct.value" :label="ct.label" :value="ct.value" />
                  </el-select>
                  <el-input v-model="field.field" placeholder="字段名（如 background）" class="!w-200px" />
                  <el-input v-model="field.title" placeholder="字段标题" class="!w-200px" />
                  <el-form-item v-if="field.controlType === 'TEXTAREA'" label="行数" label-width="50px" class="!mb-0">
                    <el-input-number v-model="field.rows" :min="1" :max="20" controls-position="right" class="!w-100px" />
                  </el-form-item>
                  <el-button link type="danger" @click="removeField(idx, fidx)"><Icon icon="ep:delete" /></el-button>
                </div>
                <el-button link type="primary" @click="addField(idx)"><Icon icon="ep:plus" />添加字段</el-button>
              </div>
            </div>
            <el-button type="primary" plain @click="addSection"><Icon icon="ep:plus" />添加章节</el-button>
          </div>
        </el-form-item>
        <el-form-item label="排除章节">
          <el-select v-model="excludedArr" multiple collapse-tags clearable class="!w-full" placeholder="选择要从模板中排除的章节（可空）">
            <el-option v-for="s in sectionsArr" :key="s.code" :label="`${s.code} - ${s.title}`" :value="s.code" :disabled="!s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节覆盖">
          <div class="vis-overrides">
            <div v-if="!overridesArr.length" class="vis-empty">暂无覆盖项，留空表示沿用章节默认必填规则</div>
            <div v-for="(ov, idx) in overridesArr" :key="idx" class="vis-override-item">
              <el-select v-model="ov.code" placeholder="选择章节" class="!w-260px" filterable>
                <el-option v-for="s in sectionsArr" :key="s.code" :label="`${s.code} - ${s.title}`" :value="s.code" :disabled="!s.code" />
              </el-select>
              <el-switch v-model="ov.required" active-text="必填" inactive-text="可选" />
              <el-input v-model="ov.remark" placeholder="覆盖说明（可空）" class="!w-280px" />
              <el-button link type="danger" @click="removeOverride(idx)"><Icon icon="ep:delete" /></el-button>
            </div>
            <el-button link type="primary" @click="addOverride"><Icon icon="ep:plus" />添加覆盖项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="变更说明" prop="changeLog">
          <el-input v-model="versionForm.changeLog" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="versionSaving" @click="saveVersion">保存</el-button>
      </template>
    </Dialog>
  </Dialog>

  <!-- 详情对话框 -->
  <Dialog v-model="detailVisible" title="文档模板详情" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="模板编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="模板名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="文档类别">
        <dict-tag :type="DICT_TYPE.PMS_DOC_CATEGORY" :value="current.docCategory" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_DOC_TEMPLATE_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="父模板">{{ parentTemplateName(current.parentTemplateId) }}</el-descriptions-item>
      <el-descriptions-item label="当前版本">{{ currentVersionLabel(current) }}</el-descriptions-item>
      <el-descriptions-item label="适用条件" :span="2">
        <div v-if="parseApplicability(current.applicability).length">
          <el-tag
            v-for="item in parseApplicability(current.applicability)"
            :key="item.key"
            size="small"
            class="mr-3px mb-3px"
          >
            {{ item.label }}
          </el-tag>
        </div>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="模板说明" :span="2">{{ current.description || '-' }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="currentVersionSections.length">
      <div class="mb-10px font-bold">当前版本章节结构（{{ currentVersionSections.length }} 章）</div>
      <el-table :data="currentVersionSections" border row-key="code">
        <el-table-column prop="order" label="序号" width="70" align="center" />
        <el-table-column prop="code" label="章节编码" width="160" />
        <el-table-column prop="title" label="章节标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="包含字段" min-width="280">
          <template #default="{ row }">
            <div v-if="row.fields && row.fields.length">
              <el-tag
                v-for="(f, i) in row.fields"
                :key="i"
                size="small"
                class="mr-3px mb-3px"
                :type="fieldTagType(f)"
              >
                {{ f.title }}（{{ fieldControlLabel(f) }}）
              </el-tag>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="覆盖/排除" width="160">
          <template #default="{ row }">
            <el-tag v-if="isSectionExcluded(row.code)" type="danger" size="small">已排除</el-tag>
            <el-tag v-else-if="getSectionOverride(row.code)" type="warning" size="small">
              {{ getSectionOverride(row.code).required ? '必填(覆盖)' : '可选(覆盖)' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as DocTemplateApi from '@/api/pms/engineering/doc-template'
import type { DocTemplateVO, DocTemplateVersionVO } from '@/api/pms/engineering/doc-template'

defineOptions({ name: 'PmsEngDocTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DocTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  docCategory: '',
  status: undefined as number | undefined
})

// 适用条件解析（用于列表/详情展示）
const applicabilityLabels: Record<string, Record<string, string>> = {
  projectType: {
    NEW_BUILD: '新建',
    UPGRADE: '升级',
    MAINTENANCE: '维保'
  },
  networkType: {
    LAN: '局域网',
    WAN: '广域网',
    CLOUD: '云网络'
  },
  productType: {
    FIREWALL: '防火墙',
    IPS: '入侵防御',
    WAF: 'Web应用防火墙',
    SWITCH: '交换机',
    ROUTER: '路由器',
    VPN: 'VPN网关',
    COMMON: '通用'
  },
  implementMode: {
    ON_SITE: '现场实施',
    REMOTE: '远程实施',
    HYBRID: '混合实施'
  }
}
const applicabilityFieldLabel: Record<string, string> = {
  projectType: '项目类型',
  networkType: '网络类型',
  productType: '产品类型',
  implementMode: '实施方式'
}

// 适用条件可视化选项（用于新建/编辑多选下拉）
const optionMap = {
  projectType: [
    { value: 'NEW_BUILD', label: '新建' },
    { value: 'UPGRADE', label: '升级' },
    { value: 'MAINTENANCE', label: '维保' }
  ],
  networkType: [
    { value: 'LAN', label: '局域网' },
    { value: 'WAN', label: '广域网' },
    { value: 'CLOUD', label: '云网络' }
  ],
  productType: [
    { value: 'FIREWALL', label: '防火墙' },
    { value: 'IPS', label: '入侵防御' },
    { value: 'WAF', label: 'Web应用防火墙' },
    { value: 'SWITCH', label: '交换机' },
    { value: 'ROUTER', label: '路由器' },
    { value: 'VPN', label: 'VPN网关' },
    { value: 'COMMON', label: '通用' }
  ],
  implementMode: [
    { value: 'ON_SITE', label: '现场实施' },
    { value: 'REMOTE', label: '远程实施' },
    { value: 'HYBRID', label: '混合实施' }
  ]
}

// 适用条件可视化对象（表单内编辑用）
const applicabilityObj = reactive<{
  projectType: string[]
  networkType: string[]
  productType: string[]
  implementMode: string[]
  priority: number
  isDefault: boolean
}>({
  projectType: [],
  networkType: [],
  productType: [],
  implementMode: [],
  priority: 0,
  isDefault: false
})
// applicability JSON 字符串 <-> 可视化对象
const parseApplicabilityToObj = (raw?: string) => {
  const obj = { projectType: [] as string[], networkType: [] as string[], productType: [] as string[], implementMode: [] as string[], priority: 0, isDefault: false }
  if (!raw) return obj
  try {
    const p = JSON.parse(raw)
    obj.projectType = Array.isArray(p.projectType) ? p.projectType : (p.projectType ? [p.projectType] : [])
    obj.networkType = Array.isArray(p.networkType) ? p.networkType : (p.networkType ? [p.networkType] : [])
    obj.productType = Array.isArray(p.productType) ? p.productType : (p.productType ? [p.productType] : [])
    obj.implementMode = Array.isArray(p.implementMode) ? p.implementMode : (p.implementMode ? [p.implementMode] : [])
    obj.priority = Number(p.priority) || 0
    obj.isDefault = !!p.isDefault
  } catch {
    // 保持默认空值
  }
  return obj
}
const stringifyApplicability = (obj: typeof applicabilityObj) => {
  return JSON.stringify({
    projectType: obj.projectType,
    networkType: obj.networkType,
    productType: obj.productType,
    implementMode: obj.implementMode,
    priority: obj.priority,
    isDefault: obj.isDefault
  })
}

const parseApplicability = (raw?: string) => {
  if (!raw) return [] as { key: string; label: string }[]
  try {
    const obj = JSON.parse(raw)
    const result: { key: string; label: string }[] = []
    Object.keys(obj || {}).forEach((k) => {
      if (k === 'priority' || k === 'isDefault') return
      if (!applicabilityFieldLabel[k]) return
      const val = obj[k]
      const valArr = Array.isArray(val) ? val : [val]
      valArr.forEach((v: string) => {
        const valLabel = applicabilityLabels[k]?.[v] || v
        result.push({ key: k + v, label: `${applicabilityFieldLabel[k]}:${valLabel}` })
      })
    })
    if (obj.isDefault) result.push({ key: 'isDefault', label: '默认模板' })
    if (obj.priority) result.push({ key: 'priority', label: `优先级:${obj.priority}` })
    return result
  } catch {
    return [] as { key: string; label: string }[]
  }
}

// 父模板名称映射
const parentTemplateMap = ref<Record<number, string>>({})
const refreshParentTemplateMap = async () => {
  const list = await DocTemplateApi.getPublishedDocTemplateList()
  const map: Record<number, string> = {}
  list.forEach((i: DocTemplateVO) => {
    if (i.id) map[i.id] = i.name
  })
  parentTemplateMap.value = map
}
const parentTemplateName = (id?: number) => (id ? parentTemplateMap.value[id] || `#${id}` : '-')

// 当前版本标签
const currentVersionLabel = (row: DocTemplateVO) => {
  if (!row.currentVersionId) return '未发布'
  return `版本#${row.currentVersionId}`
}

const load = async () => {
  loading.value = true
  try {
    const data = await DocTemplateApi.getDocTemplatePage(query)
    rows.value = data.list
    total.value = data.total
    await refreshParentTemplateMap()
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.code = ''
  query.name = ''
  query.docCategory = ''
  query.status = undefined
  query.pageNo = 1
  load()
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<DocTemplateVO>({
  code: '',
  name: '',
  docCategory: '',
  parentTemplateId: undefined,
  applicability: '{}',
  description: '',
  version: 0
})
const rules = {
  code: [{ required: true, message: '请输入模板编号' }],
  name: [{ required: true, message: '请输入模板名称' }],
  docCategory: [{ required: true, message: '请选择文档类别' }]
}
const parentTemplateOptions = ref<DocTemplateVO[]>([])
const onDocCategoryChange = async () => {
  form.parentTemplateId = undefined
  if (form.docCategory) {
    parentTemplateOptions.value = await DocTemplateApi.getPublishedDocTemplateList(form.docCategory)
  } else {
    parentTemplateOptions.value = []
  }
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    docCategory: '',
    parentTemplateId: undefined,
    applicability: '{}',
    description: '',
    currentVersionId: undefined,
    status: 0,
    version: 0
  })
  // 同步适用条件可视化对象为空
  Object.assign(applicabilityObj, {
    projectType: [],
    networkType: [],
    productType: [],
    implementMode: [],
    priority: 0,
    isDefault: false
  })
  parentTemplateOptions.value = []
  formVisible.value = true
}
const openEdit = async (row: DocTemplateVO) => {
  const detail = await DocTemplateApi.getDocTemplate(row.id!)
  Object.assign(form, detail)
  // 同步适用条件可视化对象
  Object.assign(applicabilityObj, parseApplicabilityToObj(detail.applicability))
  if (form.docCategory) {
    parentTemplateOptions.value = await DocTemplateApi.getPublishedDocTemplateList(form.docCategory)
  } else {
    parentTemplateOptions.value = []
  }
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  // 将适用条件可视化对象序列化回 JSON 字符串
  form.applicability = stringifyApplicability(applicabilityObj)
  saving.value = true
  try {
    if (form.id) {
      await DocTemplateApi.updateDocTemplate(form)
      message.success('更新成功')
    } else {
      await DocTemplateApi.createDocTemplate(form)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 详情
const detailVisible = ref(false)
const current = ref<DocTemplateVO>({})
const currentVersionSections = ref<any[]>([])
const currentVersionExcluded = ref<string[]>([])
const currentVersionOverrides = ref<Record<string, any>>({})
const openDetail = async (row: DocTemplateVO) => {
  current.value = await DocTemplateApi.getDocTemplate(row.id!)
  currentVersionSections.value = []
  currentVersionExcluded.value = []
  currentVersionOverrides.value = {}
  if (current.value.currentVersionId) {
    try {
      const version = await DocTemplateApi.getPublishedDocTemplateVersion(row.id!)
      currentVersionSections.value = parseSections(version?.sections)
      currentVersionExcluded.value = parseExcludedToArr(version?.excludedSections)
      currentVersionOverrides.value = parseOverridesObj(version?.sectionOverrides)
    } catch {
      currentVersionSections.value = []
    }
  }
  detailVisible.value = true
}
// 解析 sectionOverrides 为对象（详情用）
const parseOverridesObj = (raw?: string): Record<string, any> => {
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}
const isSectionExcluded = (code: string) => currentVersionExcluded.value.includes(code)
const getSectionOverride = (code: string) => currentVersionOverrides.value[code] || null
// 字段标签辅助
const fieldControlLabel = (f: any): string => fieldToControlType(f) === 'TEXT' ? '单行' : fieldToControlType(f) === 'TEXTAREA' ? '多行' : fieldToControlType(f) === 'NUMBER' ? '数字' : fieldToControlType(f) === 'UPLOAD' ? '上传' : '文本'
const fieldTagType = (f: any): string => {
  const ct = fieldToControlType(f)
  return ct === 'UPLOAD' ? 'success' : ct === 'NUMBER' ? 'warning' : 'info'
}
const parseSections = (raw?: string) => {
  if (!raw) return [] as any[]
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return [] as any[]
  }
}

// 章节可视化编辑器：字段控件类型选项
const controlTypeOptions = [
  { value: 'TEXT', label: '单行文本' },
  { value: 'TEXTAREA', label: '多行文本' },
  { value: 'NUMBER', label: '数字' },
  { value: 'UPLOAD', label: '文件上传' }
]
// 字段控件类型 <-> 模板字段结构 转换
const fieldToControlType = (field: any): string => {
  if (field.type === 'uploadFile') return 'UPLOAD'
  if (field.type === 'number') return 'NUMBER'
  if (field.props?.type === 'textarea') return 'TEXTAREA'
  return 'TEXT'
}
const controlTypeToField = (ct: string): any => {
  switch (ct) {
    case 'TEXTAREA':
      return { type: 'input', props: { type: 'textarea', rows: 3 } }
    case 'NUMBER':
      return { type: 'number', props: {} }
    case 'UPLOAD':
      return { type: 'uploadFile', props: {} }
    default:
      return { type: 'input', props: { type: 'input' } }
  }
}

// 章节可视化数组（版本表单内编辑用）
interface VisField {
  field: string
  title: string
  controlType: string
  rows: number
}
interface VisSection {
  code: string
  title: string
  order: number
  fields: VisField[]
}
const sectionsArr = ref<VisSection[]>([])
// 章节覆盖可视化数组
interface VisOverride {
  code: string
  required: boolean
  remark: string
}
const overridesArr = ref<VisOverride[]>([])
// 排除章节可视化数组
const excludedArr = ref<string[]>([])

// sections JSON 字符串 <-> 可视化数组
const parseSectionsToArr = (raw?: string): VisSection[] => {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return arr.map((s: any) => ({
      code: s.code || '',
      title: s.title || '',
      order: s.order || 1,
      fields: Array.isArray(s.fields)
        ? s.fields.map((f: any) => ({
            field: f.field || '',
            title: f.title || '',
            controlType: fieldToControlType(f),
            rows: f.props?.rows || 3
          }))
        : []
    }))
  } catch {
    return []
  }
}
const stringifySections = (arr: VisSection[]): string => {
  return JSON.stringify(
    arr.map((s) => ({
      code: s.code,
      title: s.title,
      order: s.order,
      fields: s.fields.map((f) => {
        const base = controlTypeToField(f.controlType)
        const props = { ...base.props }
        if (f.controlType === 'TEXTAREA') props.rows = f.rows || 3
        return { type: base.type, field: f.field, title: f.title, props }
      })
    }))
  )
}
// sectionOverrides JSON 字符串 <-> 可视化数组
const parseOverridesToArr = (raw?: string): VisOverride[] => {
  if (!raw) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object') return []
    return Object.keys(obj).map((code) => ({
      code,
      required: obj[code]?.required !== false,
      remark: obj[code]?.remark || ''
    }))
  } catch {
    return []
  }
}
const stringifyOverrides = (arr: VisOverride[]): string => {
  if (!arr.length) return ''
  const obj: Record<string, any> = {}
  arr.forEach((o) => {
    obj[o.code] = { required: o.required, remark: o.remark || undefined }
  })
  return JSON.stringify(obj)
}
// excludedSections JSON 字符串 <-> 可视化数组
const parseExcludedToArr = (raw?: string): string[] => {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
const stringifyExcluded = (arr: string[]): string => {
  return arr.length ? JSON.stringify(arr) : ''
}

// 章节编辑器操作
const addSection = () => {
  sectionsArr.value.push({ code: '', title: '', order: sectionsArr.value.length + 1, fields: [] })
}
const removeSection = (idx: number) => {
  sectionsArr.value.splice(idx, 1)
}
const moveSection = (idx: number, delta: number) => {
  const target = idx + delta
  if (target < 0 || target >= sectionsArr.value.length) return
  const tmp = sectionsArr.value[idx]
  sectionsArr.value[idx] = sectionsArr.value[target]
  sectionsArr.value[target] = tmp
}
const addField = (sectionIdx: number) => {
  sectionsArr.value[sectionIdx].fields.push({
    field: '',
    title: '',
    controlType: 'TEXTAREA',
    rows: 3
  })
}
const removeField = (sectionIdx: number, fieldIdx: number) => {
  sectionsArr.value[sectionIdx].fields.splice(fieldIdx, 1)
}
// 章节覆盖操作
const addOverride = () => {
  overridesArr.value.push({ code: '', required: true, remark: '' })
}
const removeOverride = (idx: number) => {
  overridesArr.value.splice(idx, 1)
}
// 辅助：根据章节编码获取标题
const sectionTitleByCode = (code: string) => {
  const s = sectionsArr.value.find((x) => x.code === code)
  return s ? `${s.code} - ${s.title}` : code
}

// 版本管理
const versionVisible = ref(false)
const versionLoading = ref(false)
const versionRows = ref<DocTemplateVersionVO[]>([])
const versionTemplateId = ref<number>()
const openVersionDialog = async (row: DocTemplateVO) => {
  versionTemplateId.value = row.id
  versionVisible.value = true
  await loadVersions()
}
const loadVersions = async () => {
  if (!versionTemplateId.value) return
  versionLoading.value = true
  try {
    versionRows.value = await DocTemplateApi.getDocTemplateVersionList(versionTemplateId.value)
  } finally {
    versionLoading.value = false
  }
}

// 新增版本
const versionCreateVisible = ref(false)
const versionFormRef = ref()
const versionSaving = ref(false)
const versionForm = reactive<DocTemplateVersionVO>({
  templateId: 0,
  versionLabel: '',
  sections: '[]',
  sectionOverrides: '',
  excludedSections: '',
  changeLog: ''
})
const versionRules = {
  versionLabel: [{ required: true, message: '请输入版本号' }]
}
const openVersionCreate = () => {
  Object.assign(versionForm, {
    id: undefined,
    templateId: versionTemplateId.value,
    versionLabel: '',
    sections: '[]',
    sectionOverrides: '',
    excludedSections: '',
    changeLog: ''
  })
  // 同步可视化数组为空
  sectionsArr.value = []
  overridesArr.value = []
  excludedArr.value = []
  versionCreateVisible.value = true
}
const saveVersion = async () => {
  await versionFormRef.value.validate()
  if (!sectionsArr.value.length) {
    message.warning('请至少添加一个章节')
    return
  }
  // 校验章节编码与字段名不为空
  for (const s of sectionsArr.value) {
    if (!s.code || !s.title) {
      message.warning('请完善章节编码与标题')
      return
    }
    for (const f of s.fields) {
      if (!f.field || !f.title) {
        message.warning(`章节「${s.title}」中存在未填写的字段名或标题`)
        return
      }
    }
  }
  // 将可视化数组序列化回 JSON 字符串
  versionForm.sections = stringifySections(sectionsArr.value)
  versionForm.sectionOverrides = stringifyOverrides(overridesArr.value)
  versionForm.excludedSections = stringifyExcluded(excludedArr.value)
  versionSaving.value = true
  try {
    await DocTemplateApi.createDocTemplateVersion(versionForm)
    message.success('版本创建成功')
    versionCreateVisible.value = false
    await loadVersions()
  } finally {
    versionSaving.value = false
  }
}
const handlePublishVersion = async (row: DocTemplateVersionVO) => {
  await message.confirm('确认发布此版本？发布后将作为模板的当前版本。')
  await DocTemplateApi.publishDocTemplateVersion(row.id!)
  message.success('版本发布成功')
  await loadVersions()
}

// 状态操作
const handlePublish = async (row: DocTemplateVO) => {
  await message.confirm('确认发布此文档模板？发布后不可修改。')
  await DocTemplateApi.publishDocTemplate(row.id!)
  message.success('发布成功')
  await load()
}
const handleDisable = async (row: DocTemplateVO) => {
  await message.confirm('确认停用此文档模板？停用后不可创建新实例。')
  await DocTemplateApi.disableDocTemplate(row.id!)
  message.success('停用成功')
  await load()
}
const remove = async (row: DocTemplateVO) => {
  await message.delConfirm()
  await DocTemplateApi.deleteDocTemplate(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style lang="scss" scoped>
.vis-applicability {
  width: 100%;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}
.vis-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 32px;
}
.vis-sections {
  width: 100%;
}
.vis-section-item {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}
.vis-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.vis-fields {
  padding-left: 24px;
}
.vis-field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.vis-overrides {
  width: 100%;
}
.vis-override-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.vis-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 8px 0;
}
</style>
