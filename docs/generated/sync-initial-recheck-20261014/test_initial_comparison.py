import copy
import unittest
from initial_comparison import InitialComparison,EXPECTED_ROWS

class InitialComparisonTest(unittest.TestCase):
    def policy(self):
        definition={'sources':[{'object':str(i),'table':'reuse_reusev74m_fb_contract'} for i in range(5)],'chunkSize':500}
        task={'phase':'INITIAL_MIGRATION','rows':sum(EXPECTED_ROWS),'summary':'{"CREATED":4602161,"UPDATED":120639}'}
        p=InitialComparison(definition,task,'initialv15r20261014')
        current=copy.deepcopy(definition)
        for s in current['sources']:s['table']='reuse_initialv15r20261014_fb_contract'
        return p,current,task

    def source(self,i,seconds):return {'phase':'INITIAL_MIGRATION','sourceIndex':i,'object':str(i),'rows':EXPECTED_ROWS[i],'seconds':seconds}

    def test_only_exact_helper_names_are_normalized(self):
        p,d,_=self.policy();self.assertEqual('EQUIVALENT_DEFINITION',p.observe('FULL_CHAIN_DEFINITION',d)['status'])
        p,d,_=self.policy();d['chunkSize']=1000
        with self.assertRaises(ValueError):p.observe('FULL_CHAIN_DEFINITION',d)

    def test_small_variance_and_exact_tolerance_are_allowed(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        self.assertIsNone(p.observe('FULL_CHAIN_SOURCE',self.source(0,80)))
        self.assertIsNone(p.observe('FULL_CHAIN_SOURCE',self.source(1,'85.0955940225')))

    def test_confirmed_source_overrun_records_original_baseline(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        r=p.observe('FULL_CHAIN_SOURCE',self.source(0,229.111742476))
        self.assertEqual('74.702411047',r['historicalBaselineSeconds'])
        self.assertEqual('112.0536165705',r['stopThresholdSeconds'])
        self.assertTrue(r['stopRemainingAcceptance'])

    def test_different_rows_or_source_order_not_a_performance_comparison(self):
        for event in [self.source(1,30),dict(self.source(0,30),rows=120638)]:
            p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
            with self.assertRaises(ValueError):p.observe('FULL_CHAIN_SOURCE',event)

    def test_no_timing_judgment_before_definition_check(self):
        p,_,_=self.policy()
        with self.assertRaises(ValueError):p.observe('FULL_CHAIN_PROGRESS',{'phase':'INITIAL_MIGRATION','seconds':7000})

    def test_initial_review_requires_all_sources_and_same_actions(self):
        p,d,t=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        for i in range(5):p.observe('FULL_CHAIN_SOURCE',self.source(i,50 if i<4 else 4000))
        r=p.observe('FULL_CHAIN_TASK',{**t,'seconds':4400})
        self.assertEqual('ALLOW_INITIAL_VERIFICATION',r['status'])
        with self.assertRaises(ValueError):p.observe('FULL_CHAIN_TASK',{**t,'seconds':4400,'summary':'{}'})

    def test_total_progress_uses_user_tolerance(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        self.assertIsNone(p.observe('FULL_CHAIN_PROGRESS',{'phase':'INITIAL_MIGRATION','seconds':4300}))
        r=p.observe('FULL_CHAIN_PROGRESS',{'phase':'INITIAL_MIGRATION','seconds':6400})
        self.assertEqual('6394.1310823290',r['stopThresholdSeconds'])

if __name__=='__main__':unittest.main()
