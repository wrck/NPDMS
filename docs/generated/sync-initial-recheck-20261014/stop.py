"""Stop only this identified test after the operator confirms equivalent-work regression."""
import argparse,json,pathlib
import common as c
from performance_guard import stop_test_group

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--reason',required=True)
    args=parser.parse_args()
    if not args.reason.strip():raise ValueError('Explain the equivalent-work comparison')
    assert not (c.ROOT/'performance-regression.json').exists(),'One stop decision only'
    ref=json.loads((c.ROOT/'test-process.json').read_text())
    worker=None
    state=c.ROOT/'observer-state.json'
    if state.exists():
        last=json.loads(state.read_text()).get('last_resource') or {}
        workers=[p for p in last.get('java_group',[]) if p['role']=='surefire']
        if workers:worker=workers[-1]
    c.save('performance-regression.json',{'at':c.now(),'reason':args.reason,
           'decision_basis':'Operator verified equivalent task stages, data and actions',
           'threshold_policy':'No automatic cutoff or arbitrary multiplier',
           'stop_remaining_acceptance':True})
    review=c.ROOT/'initial-review.txt'
    if not review.exists():review.write_text('STOP_PERFORMANCE\n')
    result=stop_test_group(ref,worker)
    c.save('performance-stop-result.json',{'at':c.now(),**result})
    print(json.dumps(result),flush=True)

if __name__=='__main__':main()
