<template>
  <ContentWrap>
    <h2 class="text-lg mt-0">设备连接与命令采集</h2>
    <p class="text-[var(--el-text-color-secondary)]"
      >管理命令模板与连接授权，查看每次下发命令和设备日志。</p
    >
    <el-tabs v-model="tab">
      <el-tab-pane
        label="采集任务"
        name="tasks"
        v-if="checkPermi(['pms:device-collection:query'])"
      />
      <el-tab-pane
        label="命令模板"
        name="templates"
        v-if="checkPermi(['pms:collection-template:query'])"
      />
      <el-tab-pane
        label="保存连接"
        name="connections"
        v-if="checkPermi(['pms:device-credential:query'])"
      />
    </el-tabs>
    <el-form v-if="tab !== 'templates'" label-width="70px" class="mb-16px">
      <el-form-item label="项目"
        ><PmsEntitySelect
          v-model="projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-360px max-w-full"
      /></el-form-item>
    </el-form>
    <template v-if="tab === 'tasks'">
      <el-empty v-if="!projectId" description="先选择项目，再选择设备并发起采集" />
      <template v-else
        ><el-button type="primary" @click="collection?.open(projectId)"
          >打开采集与执行历史</el-button
        ><p class="text-[var(--el-text-color-secondary)]"
          >独立采集的结果保留在当前项目。配置调试和业务联调的日志关联请从对应业务记录进入。</p
        ></template
      >
    </template>
    <TemplatePanel v-if="tab === 'templates'" />
    <ConnectionPanel
      v-if="tab === 'connections' && projectId"
      :key="projectId"
      :project-id="projectId"
    />
    <el-empty v-if="tab === 'connections' && !projectId" description="请选择连接所属项目" />
  </ContentWrap>
  <CollectionDialog ref="collection" entry="center" />
</template>
<script setup lang="ts">
import type { Id } from '@/api/pms/platform/deviceCollection'
import * as ProjectApi from '@/api/pms/project/projects'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import CollectionDialog from '@/components/DeviceCollection/CollectionDialog.vue'
import TemplatePanel from './TemplatePanel.vue'
import ConnectionPanel from './ConnectionPanel.vue'
import { checkPermi } from '@/utils/permission'
defineOptions({ name: 'PmsDeviceCollection' })
const tab = ref(
  checkPermi(['pms:device-collection:query'])
    ? 'tasks'
    : checkPermi(['pms:collection-template:query'])
      ? 'templates'
      : 'connections'
)
const projectId = ref<Id>()
const collection = ref<InstanceType<typeof CollectionDialog>>()
</script>
