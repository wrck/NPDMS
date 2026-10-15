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

    def test_partial_source_exceeds_entire_budget_without_extrapolation(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        event=dict(self.source(0,'112.0536165706'),rows=1000)
        r=p.observe('FULL_CHAIN_SOURCE_PROGRESS',event)
        self.assertFalse(r['measurementComplete'])
        self.assertEqual('ELAPSED_LOWER_BOUND_EXCEEDS_FULL_SOURCE_BUDGET',r['reason'])
        self.assertEqual(1000,r['observedRows'])
        self.assertEqual(120639,r['expectedRows'])
        self.assertEqual(set(),p.completed)

    def test_partial_equal_threshold_does_not_stop_or_complete(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        self.assertIsNone(p.observe('FULL_CHAIN_SOURCE_PROGRESS',dict(self.source(0,'112.0536165705'),rows=1000)))
        self.assertEqual(set(),p.completed)

    def test_partial_requires_definition_identity_and_current_source(self):
        for changes in ({'sourceIndex':1},{'object':'wrong'},{'rows':-1},{'rows':120640},{'rows':True},{'sourceIndex':False}):
            p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
            with self.assertRaises(ValueError):p.observe('FULL_CHAIN_SOURCE_PROGRESS',dict(self.source(0,1),**changes))
        p,_,_=self.policy()
        with self.assertRaises(ValueError):p.observe('FULL_CHAIN_SOURCE_PROGRESS',dict(self.source(0,1),rows=0))

    def test_progress_must_be_monotone(self):
        for changes in ({'rows':499},{'seconds':9}):
            p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
            p.observe('FULL_CHAIN_SOURCE_PROGRESS',dict(self.source(0,10),rows=500))
            event=dict(self.source(0,11),rows=500);event.update(changes)
            with self.assertRaises(ValueError):p.observe('FULL_CHAIN_SOURCE_PROGRESS',event)

    def test_last_source_has_no_fabricated_source_budget(self):
        p,d,_=self.policy();p.observe('FULL_CHAIN_DEFINITION',d)
        for i in range(4):p.observe('FULL_CHAIN_SOURCE',self.source(i,1))
        self.assertIsNone(p.observe('FULL_CHAIN_SOURCE_PROGRESS',dict(self.source(4,6000),rows=500)))
        self.assertEqual({0,1,2,3},p.completed)

if __name__=='__main__':unittest.main()
