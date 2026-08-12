<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="方案编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="方案名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-solution:create']"
          ><Icon icon="ep:plus" />新增方案</el-button
        >
        <el-button type="success" @click="openGenerateDraft()" v-hasPermi="['pms:eng-solution:create']"
          ><Icon icon="ep:magic-stick" />从工勘/需求生成草稿</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="方案编码" min-width="140" />
      <el-table-column prop="name" label="方案名称" min-width="180" />
      <el-table-column prop="reviewLevel" label="审核级别" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_REVIEW_LEVEL" :value="row.reviewLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="versionLabel" label="版本" width="90" />
      <el-table-column prop="baselineVersion" label="基线版本" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-solution:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleSimpleAction(row, 'submit')"
            v-hasPermi="['pms:eng-solution:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleSimpleAction(row, 'startReview')"
            v-hasPermi="['pms:eng-solution:update']"
            >开始审核</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="openApprove(row, 'approve')"
            v-hasPermi="['pms:eng-solution:audit']"
            >通过</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2"
            @click="openApprove(row, 'reject')"
            v-hasPermi="['pms:eng-solution:audit']"
            >驳回</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1"
            @click="handleSimpleAction(row, 'withdraw')"
            v-hasPermi="['pms:eng-solution:update']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="![3, 4, 5, 6].includes(row.status)"
            @click="handleSimpleAction(row, 'terminate')"
            v-hasPermi="['pms:eng-solution:update']"
            >终止</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-solution:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑方案' : '新增方案'" width="860px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案类型" prop="solutionType"><el-input v-model="form.solutionType" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="方案背景" prop="background">
            <Editor v-model="form.background" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="实施目标" prop="target">
            <Editor v-model="form.target" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实施团队" prop="team"><el-input v-model="form.team" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="版本标签" prop="versionLabel"><el-input v-model="form.versionLabel" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="物料清单" prop="inventory">
            <Editor v-model="form.inventory" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="实施计划" prop="plan">
            <Editor v-model="form.plan" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="拓扑描述" prop="topology">
            <Editor v-model="form.topology" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="接口规划" prop="interfacePlan"><el-input v-model="form.interfacePlan" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="IP 规划" prop="ipPlan"><el-input v-model="form.ipPlan" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="实施脚本" prop="script">
            <Editor v-model="form.script" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="质量保障" prop="quality"><el-input v-model="form.quality" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险控制" prop="risk"><el-input v-model="form.risk" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="运维要求" prop="oAndM"><el-input v-model="form.oAndM" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="审核级别" prop="reviewLevel">
            <el-select v-model="form.reviewLevel" class="!w-full">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.PMS_REVIEW_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="approveVisible" :title="approveAction === 'approve' ? '审核通过' : '审核驳回'" width="520px">
    <el-form :model="approveForm" label-width="100px">
      <el-form-item label="方案编码">
        <span>{{ approveForm.code }}</span>
      </el-form-item>
      <el-form-item label="审核意见">
        <el-input v-model="approveForm.approvalOpinion" type="textarea" :rows="4" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button :type="approveAction === 'approve' ? 'success' : 'warning'" :loading="saving" @click="submitApprove"
        >确认</el-button
      >
    </template>
  </Dialog>

  <Dialog v-model="generateVisible" title="从已确认工勘 + 已生效需求生成方案草稿" width="520px">
    <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="100px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="generateForm.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
        />
      </el-form-item>
      <el-form-item label="方案编码" prop="solutionCode">
        <el-input v-model="generateForm.solutionCode" />
      </el-form-item>
      <el-form-item label="方案名称" prop="solutionName">
        <el-input v-model="generateForm.solutionName" placeholder="留空则按编码生成" />
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        title="服务端将自动汇总已确认工勘与已生效需求关键字段到新方案草稿，并写入来源追溯记录。"
      />
    </el-form>
    <template #footer>
      <el-button @click="generateVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitGenerate">生成</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SolutionApi from '@/api/pms/engineering/solution'
import type { SolutionApproveVO, SolutionGenerateDraftVO, SolutionVO } from '@/api/pms/engineering/solution'
import * as ProjectApi from '@/api/pms/project/project'

defineOptions({ name: 'PmsEngSolution' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SolutionVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', name: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SolutionVO>({ projectId: 0, code: '', name: '', reviewLevel: 0 })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入方案编码' }],
  name: [{ required: true, message: '请输入方案名称' }]
}

const approveVisible = ref(false)
const approveAction = ref<'approve' | 'reject'>('approve')
const approveForm = reactive<SolutionApproveVO & { code?: string }>({ id: 0, approvalOpinion: '', version: undefined })

const generateVisible = ref(false)
const generateFormRef = ref()
const generateForm = reactive<SolutionGenerateDraftVO>({ projectId: 0, solutionCode: '', solutionName: '' })
const generateRules = {
  projectId: [{ required: true, message: '请选择项目' }],
  solutionCode: [{ required: true, message: '请输入方案编码' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SolutionApi.getSolutionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SolutionVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      name: '',
      solutionType: '',
      background: '',
      target: '',
      team: '',
      inventory: '',
      plan: '',
      topology: '',
      interfacePlan: '',
      ipPlan: '',
      versionLabel: '',
      script: '',
      quality: '',
      risk: '',
      oAndM: '',
      reviewLevel: 0,
      remark: '',
      version: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await SolutionApi.updateSolution(form) : await SolutionApi.createSolution(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SolutionVO) => {
  await message.delConfirm()
  await SolutionApi.deleteSolution(row.id!)
  message.success('删除成功')
  await load()
}
const handleSimpleAction = async (
  row: SolutionVO,
  action: 'submit' | 'startReview' | 'withdraw' | 'terminate'
) => {
  const actionText = { submit: '提交', startReview: '开始审核', withdraw: '撤回', terminate: '终止' }[action]
  await message.confirm(`确认${actionText}方案【${row.code}】？`)
  if (action === 'submit') await SolutionApi.submitSolution(row.id!)
  if (action === 'startReview') await SolutionApi.startReviewSolution(row.id!)
  if (action === 'withdraw') await SolutionApi.withdrawSolution(row.id!)
  if (action === 'terminate') await SolutionApi.terminateSolution(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const openApprove = (row: SolutionVO, action: 'approve' | 'reject') => {
  approveAction.value = action
  Object.assign(approveForm, { id: row.id, code: row.code, approvalOpinion: '', version: row.version })
  approveVisible.value = true
}
const submitApprove = async () => {
  saving.value = true
  try {
    if (approveAction.value === 'approve') {
      await SolutionApi.approveSolution({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    } else {
      await SolutionApi.rejectSolution({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    }
    message.success('操作成功')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const openGenerateDraft = () => {
  Object.assign(generateForm, { projectId: 0, solutionCode: '', solutionName: '' })
  generateVisible.value = true
}
const submitGenerate = async () => {
  await generateFormRef.value.validate()
  saving.value = true
  try {
    await SolutionApi.generateDraft(generateForm)
    message.success('草稿生成成功')
    generateVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
