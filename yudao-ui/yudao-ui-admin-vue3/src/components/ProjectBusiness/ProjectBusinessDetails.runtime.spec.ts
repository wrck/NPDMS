import {expect,it} from 'vitest'
import {mount,tableColumn} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Details from './ProjectBusinessDetails.vue'

const fields=[{code:'lines',name:'明细',readable:true,writable:true,children:[
  {code:'quantity',name:'数量',type:'NUMBER',readable:true,writable:true,required:true},
  {code:'enabled',name:'启用',type:'BOOLEAN',readable:true,writable:true,required:true},
  {code:'serial',name:'生成编号',type:'TEXT',readable:true,writable:false}
]}]
it('retains child identity, zero and false; omits hidden tabs from the patch',async()=>{
  const mounted=mount(Details,{fields:[...fields,{...fields[0],code:'hidden',tabVisible:false}],initialValues:{lines:[{id:'11',version:2,quantity:1,enabled:true,serial:'server-owned'}],hidden:[{id:'12'}]}},{ElTableColumn:tableColumn})
  try{const state=(mounted.vm as any).$.setupState;state.rows.lines[0].quantity=0;state.rows.lines[0].enabled=false
    expect(await (mounted.vm as any).buildInput()).toEqual({lines:[{id:'11',version:2,quantity:0,enabled:false}]})
    state.rows.lines[0].quantity=null;await expect((mounted.vm as any).buildInput()).rejects.toThrow('数量不能为空')
  }finally{mounted.app.unmount()}
})
it('never submits readonly detail changes',async()=>{
  const mounted=mount(Details,{fields,disabled:true,initialValues:{lines:[]}},{ElTableColumn:tableColumn})
  try{(mounted.vm as any).$.setupState.rows.lines.push({quantity:9,enabled:true});expect(await (mounted.vm as any).buildInput()).toEqual({})}finally{mounted.app.unmount()}
})
