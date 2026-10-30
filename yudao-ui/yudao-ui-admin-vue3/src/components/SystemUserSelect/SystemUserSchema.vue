<template><component :is="field" v-bind="$attrs" /></template>
<script setup lang="ts">
import { useApiSelect } from '@/components/FormCreate/src/components/useApiSelect'
defineOptions({ inheritAttrs: false })
const field = useApiSelect({
  name: 'UserSelect',
  labelField: 'nickname',
  valueField: 'id',
  url: '/system/user/simple-list',
  userSelection: true
})
</script>
