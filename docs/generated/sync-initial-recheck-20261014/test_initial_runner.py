import ast
import pathlib
import signal
import unittest
from performance_guard import stop_test_group

ROOT=pathlib.Path(__file__).resolve().parent

class InitialRunnerTest(unittest.TestCase):
    def ref(self):
        return {'maven_pid':987654,'maven_pgid':987654,'maven_proc_start_ticks':100}

    def test_reused_pid_cannot_be_signalled(self):
        signals=[]
        with self.assertRaises(RuntimeError):
            stop_test_group(self.ref(),identity=lambda pid:(987654,101),killpg=lambda *x:signals.append(x))
        self.assertEqual([],signals)

    def test_exited_process_is_not_signalled(self):
        signals=[]
        result=stop_test_group(self.ref(),identity=lambda pid:None,killpg=lambda *x:signals.append(x))
        self.assertIsNone(result['signal']);self.assertEqual([],signals)

    def test_other_process_group_is_refused(self):
        with self.assertRaises(RuntimeError):
            stop_test_group(self.ref(),identity=lambda pid:(999,100),killpg=lambda *x:self.fail('unsafe signal'))

    def test_term_waits_for_identified_exit(self):
        alive=[True];signals=[]
        def kill(group, sig):
            signals.append((group,sig));alive[0]=False
        result=stop_test_group(self.ref(),identity=lambda pid:(987654,100) if alive[0] else None,killpg=kill,sleep=lambda x:None)
        self.assertEqual([(987654,signal.SIGTERM)],signals)
        self.assertTrue(result['identified_processes_exited'])

    def test_remaining_worker_is_checked_when_leader_exits(self):
        alive=[True];signals=[]
        def identity(pid):return (987654,200) if pid==987655 and alive[0] else None
        def kill(group,sig):signals.append(sig);alive[0]=False
        stop_test_group(self.ref(),{'pid':987655,'process_start_ticks':200},identity=identity,killpg=kill,sleep=lambda x:None)
        self.assertEqual([signal.SIGTERM],signals)

    def test_same_identity_remaining_requires_kill(self):
        signals=[]
        result=stop_test_group(self.ref(),identity=lambda pid:(987654,100),killpg=lambda *x:signals.append(x),sleep=lambda x:None)
        self.assertEqual([signal.SIGTERM,signal.SIGKILL],[x[1] for x in signals])
        self.assertTrue(result['completion_requires_launcher_receipt'])

    def test_runtime_command_initial_only_and_bounded_exit_jfr(self):
        source=(ROOT/'common.py').read_text()
        self.assertIn('-Dpms.sync.mysql.fullChain.initialOnly=true',source)
        self.assertIn('dumponexit=true,maxsize=64m',source)
        self.assertIn("PHASES=['INITIAL_MIGRATION']",source)
        self.assertNotIn('reusev154m',source)

    def test_monitor_does_not_attach_heap_or_reset_counters(self):
        source=(ROOT/'observer.py').read_text()
        self.assertNotIn("'GC.heap_info'",source)
        self.assertNotIn("/'jcmd'",source)
        self.assertNotIn('RESET MASTER',source)
        self.assertNotIn('/var/lib/docker',source)

    def test_failure_finalizes_before_large_scans(self):
        source=(ROOT/'finalize.py').read_text()
        branch=source.index('if not passed:')
        returned=source.index('return 1',branch)
        self.assertLess(returned,source.index("counts=c.rows('after/source-counts'"))
        self.assertIn("'correctness_scans_executed':False",source[branch:returned])

    def test_all_scripts_parse(self):
        for p in ROOT.glob('*.py'):ast.parse(p.read_text(),filename=str(p))

if __name__=='__main__':unittest.main()
