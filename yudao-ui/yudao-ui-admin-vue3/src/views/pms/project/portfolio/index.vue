<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="组合编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="组合名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PORTFOLIO_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="成员类型" prop="memberType">
        <el-select v-model="query.memberType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PORTFOLIO_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:portfolio:create']"
          ><Icon icon="ep:plus" />新增组合</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="组合编码" min-width="120" />
      <el-table-column prop="name" label="组合名称" min-width="160" />
      <el-table-column prop="purpose" label="用途" min-width="100" />
      <el-table-column prop="memberType" label="成员类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PORTFOLIO_TYPE" :value="row.memberType" />
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" label="成员数" width="90" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PORTFOLIO_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openMembers(row)" v-hasPermi="['pms:portfolio:query']"
            >成员</el-button
          >
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:portfolio:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            @click="handlePublish(row)"
            v-hasPermi="['pms:portfolio:publish']"
            :disabled="row.status !== 0"
            >发布</el-button
          >
          <el-button
            link
            type="warning"
            @click="handleRecalculate(row)"
            v-hasPermi="['pms:portfolio:update']"
            :disabled="row.memberType !== 'DYNAMIC'"
            >重算</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:portfolio:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <!-- 组合表单 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑项目组合' : '新增项目组合'" width="820px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="组合编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="组合名称" prop="name">
            <el-input v-model="form.name" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="用途" prop="purpose">
            <el-select v-model="form.purpose" class="!w-full" allow-create filterable clearable>
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PORTFOLIO_CATEGORY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="成员类型" prop="memberType">
            <el-radio-group v-model="form.memberType" :disabled="!!form.id && form.status !== 0">
              <el-radio value="STATIC">静态</el-radio>
              <el-radio value="DYNAMIC">动态</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="有效期开始" prop="validFrom">
            <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="有效期结束" prop="validTo">
            <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="统计目标" prop="targetMetrics">
            <el-input v-model="form.targetMetrics" placeholder='例如：{"count":10}' />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 静态成员：项目选择 -->
      <template v-if="form.memberType === 'STATIC'">
        <el-divider content-position="left">静态成员项目</el-divider>
        <div v-for="(_, idx) in form.staticProjectIds" :key="idx" class="mb-8px">
          <el-row :gutter="8" align="middle">
            <el-col :span="20">
              <PmsEntitySelect
                v-model="form.staticProjectIds[idx]"
                :api="ProjectApi.getProjectPage"
                :label-field="['code', 'name']"
                value-field="id"
                query-field="name"
                placeholder="请选择项目"
              />
            </el-col>
            <el-col :span="4">
              <el-button link type="danger" @click="form.staticProjectIds.splice(idx, 1)"
                ><Icon icon="ep:delete"
              /></el-button>
            </el-col>
          </el-row>
        </div>
        <el-button type="primary" plain size="small" @click="form.staticProjectIds.push(undefined)">
          <Icon icon="ep:plus" />添加项目
        </el-button>
      </template>

      <!-- 动态规则编辑 -->
      <template v-if="form.memberType === 'DYNAMIC'">
        <el-divider content-position="left">动态规则（AND 逻辑）</el-divider>
        <el-table :data="form.rules" border size="small">
          <el-table-column label="规则字段" width="160">
            <template #default="{ row }">
              <el-select v-model="row.ruleField" class="!w-full">
                <el-option
                  v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PORTFOLIO_RULE_DIMENSION)"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作符" width="120">
            <template #default="{ row }">
              <el-select v-model="row.ruleOperator" class="!w-full">
                <el-option
                  v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PORTFOLIO_RULE_OPERATOR)"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="规则值">
            <template #default="{ row }">
              <el-input v-model="row.ruleValue" placeholder="IN 用逗号分隔" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="form.rules!.splice($index, 1)"
                ><Icon icon="ep:delete"
              /></el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button type="primary" plain size="small" class="mt-8px" @click="addRule">
          <Icon icon="ep:plus" />添加规则
        </el-button>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 成员列表 -->
  <Dialog v-model="membersVisible" :title="`${selectedPortfolio?.name || ''} - 组合成员`" width="920px">
    <el-button type="primary" class="mb-12px" @click="handleRecalculate(selectedPortfolio!)" v-if="selectedPortfolio?.memberType === 'DYNAMIC'">
      <Icon icon="ep:refresh" />重新计算
    </el-button>
    <el-table :data="members" v-loading="membersLoading">
      <el-table-column prop="projectCode" label="项目编码" min-width="120" />
      <el-table-column prop="projectName" label="项目名称" min-width="160" />
      <el-table-column prop="inclusionType" label="纳入类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.inclusionType === 'DYNAMIC' ? 'warning' : 'primary'">
            {{ row.inclusionType === 'DYNAMIC' ? '动态' : '静态' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '纳入' : '排除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inclusionReason" label="纳入原因" min-width="200" show-overflow-tooltip />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as PortfolioApi from '@/api/pms/project/portfolio'
import * as ProjectApi from '@/api/pms/project/project'
import type { PortfolioMemberVO, PortfolioRuleVO, PortfolioVO } from '@/api/pms/project/portfolio'

defineOptions({ name: 'PmsProjectPortfolio' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<PortfolioVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', name: '', status: undefined, memberType: '' })
const formVisible = ref(false)
const formRef = ref()
type PortfolioForm = Omit<PortfolioVO, 'staticProjectIds' | 'rules'> & {
  staticProjectIds: Array<number | undefined>
  rules: PortfolioRuleVO[]
}

const form = reactive<PortfolioForm>({
  code: '',
  name: '',
  status: 0,
  memberType: 'STATIC',
  staticProjectIds: [],
  rules: []
})
const rules = {
  code: [{ required: true, message: '请输入组合编码' }],
  name: [{ required: true, message: '请输入组合名称' }],
  memberType: [{ required: true, message: '请选择成员类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await PortfolioApi.getPortfolioPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const openForm = async (row?: PortfolioVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      code: '',
      name: '',
      purpose: '',
      validFrom: '',
      validTo: '',
      status: 0,
      targetMetrics: '',
      memberType: 'STATIC',
      staticProjectIds: [],
      rules: [],
      version: undefined
    },
    row ? { ...row, staticProjectIds: row.staticProjectIds ?? [], rules: row.rules ?? [] } : {}
  )
  // 编辑时加载详情（含规则）
  if (row?.id) {
    const detail = await PortfolioApi.getPortfolio(row.id)
    Object.assign(form, {
      rules: detail.rules ?? [],
      staticProjectIds: detail.staticProjectIds ?? []
    })
  }
  formVisible.value = true
}

const addRule = () => {
  form.rules.push({ ruleField: 'CUSTOMER', ruleOperator: 'EQ', ruleValue: '' } as PortfolioRuleVO)
}

const save = async () => {
  await formRef.value.validate()
  // 清理空规则与空项目
  const payload: PortfolioVO = {
    ...form,
    staticProjectIds: form.staticProjectIds.filter((id): id is number => Boolean(id))
  }
  if (payload.memberType === 'DYNAMIC') {
    payload.rules = (payload.rules || []).filter((r) => r.ruleValue)
    delete payload.staticProjectIds
  } else {
    delete payload.rules
  }
  saving.value = true
  try {
    payload.id ? await PortfolioApi.updatePortfolio(payload) : await PortfolioApi.createPortfolio(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (row: PortfolioVO) => {
  await message.delConfirm()
  await PortfolioApi.deletePortfolio(row.id!)
  message.success('删除成功')
  await load()
}

const handlePublish = async (row: PortfolioVO) => {
  await message.confirm(`确认发布组合【${row.name}】？发布后将计算成员并生成快照。`)
  await PortfolioApi.publishPortfolio(row.id!)
  message.success('发布成功')
  await load()
}

const handleRecalculate = async (row: PortfolioVO) => {
  await message.confirm(`确认重新计算组合【${row.name}】的动态成员？`)
  await PortfolioApi.recalculatePortfolio(row.id!)
  message.success('重新计算成功')
  await load()
  if (membersVisible.value && selectedPortfolio.value?.id === row.id) {
    await loadMembers(row.id!)
  }
}

// 成员列表
const membersVisible = ref(false)
const membersLoading = ref(false)
const selectedPortfolio = ref<PortfolioVO>()
const members = ref<PortfolioMemberVO[]>([])
const openMembers = async (row: PortfolioVO) => {
  selectedPortfolio.value = row
  membersVisible.value = true
  await loadMembers(row.id!)
}
const loadMembers = async (portfolioId: number) => {
  membersLoading.value = true
  try {
    members.value = await PortfolioApi.getPortfolioMembers(portfolioId)
  } finally {
    membersLoading.value = false
  }
}

onMounted(load)
</script>
