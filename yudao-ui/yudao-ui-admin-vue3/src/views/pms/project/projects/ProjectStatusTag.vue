<template>
  <el-tag v-if="!display.stage" :type="display.type">{{ display.label }}</el-tag>
  <dict-tag v-else-if="display.value" :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="display.value" />
  <span v-else>—</span>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { projectStatus, type ProjectStatusSource } from './projectStatus'

const props = defineProps<{ project?: ProjectStatusSource | null }>()
const display = computed(() => projectStatus(props.project))
</script>
