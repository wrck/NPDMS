import {defineComponent,h,nextTick,ref} from 'vue'
import {expect,it,vi} from 'vitest'
import {mount} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Form from './RequirementBusinessForm.vue'
import ProjectBusinessForm from '@/components/ProjectBusiness/ProjectBusinessForm.vue'
vi.mock('@form-create/element-ui',()=>({default:{component:vi.fn(),parseJson:JSON.parse}}))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents',()=>({registerDynamicFormComponents:vi.fn()}))
vi.mock('@/views/pms/project/project-master-detail/components/formCreateKeyboardRows',()=>({vFormCreateKeyboardRows:{}}))
vi.mock('./RequirementBusinessFiles.vue',()=>({default:{render:()=>null}}))
vi.mock('@/components/BusinessEntity/BusinessEntityForm.vue',()=>({default:{render:()=>null}}))
vi.mock('@/components/ProjectBusiness/ProjectBusinessContentForm.vue',()=>({default:{render:()=>null}}))
const flush=async()=>{for(let i=0;i<6;i++){await Promise.resolve();await nextTick()}}
it('keeps edited rich text and false extensions when saving temporarily disables the renderer',async()=>{
  const disabled=ref(false),form=ref<any>()
  const initialValues={content:'saved'}
  const presentation={layout:{formConfJson:'{}',formRulesJson:JSON.stringify([{type:'Editor',field:'CONTENT'},{type:'switch',field:'FLAG'},{type:'group',field:'ROWS'}]),binding:{fieldBindings:{CONTENT:'content',FLAG:'flag',ROWS:'rows'}}},definitions:[{code:'flag'},{code:'rows'}],extensions:{version:0,fields:{flag:true}}}
  const mounted=mount(defineComponent({setup:()=>()=>h(Form,{ref:form,api:{base:'/api/v1/pms/test'} as any,scopeProjectId:20,disabled:disabled.value,writableFields:[{code:'content',readable:true,writable:true}] as any,initialValues,presentation:presentation as any})}))
  try{
    await flush();const state=form.value.$.setupState;state.values.CONTENT='<p>unsaved</p>';state.values.FLAG=false
    disabled.value=true;await flush();disabled.value=false;await flush()
    expect(await form.value.readValues()).toEqual({fields:{content:'<p>unsaved</p>'},extensions:{flag:false,rows:[]}})
  }finally{mounted.app.unmount()}
})
it('renders readonly attachment context through the common form wrapper',async()=>{
  let renderedRules:any[]=[]
  const formCreate=defineComponent({props:['rule'],setup:props=>()=>{renderedRules=props.rule;return h('div')}})
  const presentation={layout:{formConfJson:'{}',formRulesJson:JSON.stringify([{type:'PmsFileArtifact',field:'SCOPE_FILES'}]),binding:{formRevisionId:'101',version:1,fieldBindings:{}}},definitions:[],extensions:{version:0,fields:{}},context:{attachments:[{key:{purposeCode:'FORM_FIELD_ATTACHMENT/SCOPE_FILES'},activeFacts:[{fileId:'42'}]}]}}
  const mounted=mount(ProjectBusinessForm,{api:{base:'/api/v1/pms/requirement-analyses'},formComponent:Form,current:{ref:{entityId:'11'},concurrencyBasis:2},scopeProjectId:20,writableFields:[],initialValues:{},presentation,disabled:true,readonly:true},{FormCreate:formCreate})
  try{
    await flush()
    expect(renderedRules).toMatchObject([{type:'RequirementBusinessFiles',props:{apiBase:'/api/v1/pms/requirement-analyses',entityId:'11',fieldKey:'SCOPE_FILES',readonly:true,currentFacts:[{fileId:'42'}]}}])
  }finally{mounted.app.unmount()}
})
