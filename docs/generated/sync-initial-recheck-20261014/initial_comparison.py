"""Compare equivalent full-chain work with the user's explicit 1.5x stop tolerance."""
import json
import re
from decimal import Decimal

BASELINE_TOTAL=Decimal('4262.754054886')
BASELINE_SOURCES=tuple(map(Decimal,('74.702411047','56.730396015','59.773085646','176.516666821')))
EXPECTED_ROWS=(120639,81522,120639,400000,4000000)
STOP_MULTIPLIER=Decimal('1.5')

def normalize_definition(value, suffix):
    if isinstance(value,dict):return {k:normalize_definition(v,suffix) for k,v in value.items()}
    if isinstance(value,list):return [normalize_definition(v,suffix) for v in value]
    if isinstance(value,str):
        for name in ('fb_contract','fb_shipment','fb_shipment_barcode'):
            value=re.sub(r'\b'+re.escape('reuse_'+suffix+'_'+name)+r'\b',name,value)
    return value

def actions(value):
    return json.loads(value) if isinstance(value,str) else value

class InitialComparison:
    def __init__(self,definition,task,current_suffix):
        self.baseline_definition=normalize_definition(definition,'reusev74m')
        self.baseline_task=task
        self.current_suffix=current_suffix
        self.definition=None
        self.completed=set()
        self.progress={}
        self.stopped=False
        if task['phase']!='INITIAL_MIGRATION' or task['rows']!=sum(EXPECTED_ROWS):
            raise ValueError('Wrong historical initial task')

    def time_decision(self,elapsed,baseline,dimension,index=None):
        value=Decimal(str(elapsed))
        if not value.is_finite() or value<0:raise ValueError('Invalid duration')
        threshold=baseline*STOP_MULTIPLIER
        if value<=threshold:return None
        self.stopped=True
        return {'status':'PERFORMANCE_REGRESSION','dimension':dimension,'sourceIndex':index,
                'observedSeconds':str(value),'historicalBaselineSeconds':str(baseline),
                'stopMultiplier':str(STOP_MULTIPLIER),'stopThresholdSeconds':str(threshold),
                'equivalentDefinitionVerified':True,'stopRemainingAcceptance':True}

    def observe(self,tag,event):
        if self.stopped:return None
        if tag=='FULL_CHAIN_DEFINITION':
            normalized=normalize_definition(event,self.current_suffix)
            if normalized!=self.baseline_definition:raise ValueError('Task definition differs beyond exact helper names')
            self.definition=event
            return {'status':'EQUIVALENT_DEFINITION'}
        if tag not in ('FULL_CHAIN_SOURCE','FULL_CHAIN_SOURCE_PROGRESS','FULL_CHAIN_PROGRESS','FULL_CHAIN_TASK'):return None
        if self.definition is None:raise ValueError('Task definition must be checked before performance')
        if event['phase']!='INITIAL_MIGRATION':raise ValueError('Unexpected later phase')
        if tag in ('FULL_CHAIN_SOURCE','FULL_CHAIN_SOURCE_PROGRESS'):
            index=event['sourceIndex']
            if type(index) is not int or index!=len(self.completed) or not 0<=index<5:
                raise ValueError('Source order/index differs from baseline')
            complete=tag=='FULL_CHAIN_SOURCE'
            rows=event['rows']
            if type(rows) is not int or rows<0 or rows>EXPECTED_ROWS[index]:raise ValueError('Invalid source row count')
            if complete and rows!=EXPECTED_ROWS[index]:raise ValueError('Completed source row count differs')
            source=self.definition['sources'][index]
            for key in ('object','sourceObject'):
                if source.get(key) is not None and event.get(key)!=source[key]:raise ValueError('Source identity differs')
            elapsed=Decimal(str(event['seconds']))
            if not elapsed.is_finite() or elapsed<0:raise ValueError('Invalid source duration')
            previous=self.progress.get(index)
            if previous and (rows<previous[0] or elapsed<previous[1]):raise ValueError('Source progress went backwards')
            self.progress[index]=(rows,elapsed)
            if complete:self.completed.add(index)
            decision=self.time_decision(elapsed,BASELINE_SOURCES[index],'source',index) if index<4 else None
            if decision:
                decision.update(measurementComplete=complete,observedRows=rows,expectedRows=EXPECTED_ROWS[index],
                    reason='COMPLETED_SOURCE_OVER_BUDGET' if complete else 'ELAPSED_LOWER_BOUND_EXCEEDS_FULL_SOURCE_BUDGET')
            return decision
        decision=self.time_decision(event['seconds'],BASELINE_TOTAL,'task')
        if decision:return decision
        if tag=='FULL_CHAIN_TASK':
            if self.completed!=set(range(5)):raise ValueError('Incomplete or different source chain')
            if event['rows']!=self.baseline_task['rows'] or actions(event['summary'])!=actions(self.baseline_task['summary']):
                raise ValueError('Completed task row count or business actions differ')
            return {'status':'ALLOW_INITIAL_VERIFICATION','equivalentDefinitionVerified':True,
                    'rowCountsAndActionsMatched':True,'historicalBaselineSeconds':str(BASELINE_TOTAL),
                    'observedSeconds':str(event['seconds']),'stopMultiplier':str(STOP_MULTIPLIER),
                    'stopThresholdSeconds':str(BASELINE_TOTAL*STOP_MULTIPLIER)}
        return None
