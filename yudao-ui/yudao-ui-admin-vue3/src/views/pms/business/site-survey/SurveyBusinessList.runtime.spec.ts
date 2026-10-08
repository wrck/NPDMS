import {expect,it,vi} from 'vitest'
import {mount,tableColumn} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import List from './SurveyBusinessList.vue'
vi.mock('@/utils/dict',()=>({DICT_TYPE:{PMS_SITE_SURVEY_STATUS:'survey'},getIntDictOptions:()=>[]}))
vi.mock('@/api/pms/project/projects',()=>({getProjectPage:vi.fn()}))
vi.mock('@/components/PmsEntitySelect/index.vue',()=>({default:{render:()=>null}}))
it('legacy-looking survey filters use shared typed filters and action availability',()=>{
  const search=vi.fn()
  const mounted=mount(List,{rows:[],total:0,page:1,loading:false,projectId:20,actionsFor:()=>[{code:'create',executable:false}],onSearch:search},{ElTableColumn:tableColumn})
  try {
    const state=(mounted.vm as any).$.setupState
    expect(state.canCreate).toBe(false)
    state.name='survey';state.status=0;state.search()
    expect(search).toHaveBeenCalledWith([{fieldCode:'name',operator:'LIKE',values:['survey']},{fieldCode:'status',operator:'EQ',values:[0]}])
  }finally{mounted.app.unmount()}
})
