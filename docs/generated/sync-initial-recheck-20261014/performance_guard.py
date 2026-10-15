"""Fail an acceptance run when a measured step exceeds its accepted baseline.

This module makes decisions only. Its caller must stop the authorized test and
must not execute later correctness stages after a returned failure. The decision object never rewrites a baseline or treats an aborted run as a pass.
The separate stop helper verifies the isolated process group identity before signalling.
"""
from decimal import Decimal, InvalidOperation
import os
import pathlib
import signal
import time


def process_identity(pid):
    try:
        text = pathlib.Path('/proc', str(pid), 'stat').read_text()
    except FileNotFoundError:
        return None
    fields = text[text.rfind(')') + 2:].split()
    if fields[0] == 'Z':
        return None
    return int(fields[2]), int(fields[19])


def stop_test_group(reference, worker=None, *, identity=process_identity,
                    killpg=os.killpg, sleep=time.sleep):
    """Signal only a still-identified member's original, isolated test group."""
    pid = reference['maven_pid']
    pgid = reference['maven_pgid']
    if pid != pgid or pgid <= 1 or pgid == os.getpgrp():
        raise ValueError('Not an isolated Maven process group')
    expected = {pid: reference['maven_proc_start_ticks']}
    if worker is not None:
        expected[worker['pid']] = worker['process_start_ticks']

    def owned_alive():
        matches = False
        for member, started in expected.items():
            current = identity(member)
            if current is not None:
                if current != (pgid, started):
                    raise RuntimeError('Process identity changed; refuse group signal')
                matches = True
        return matches

    if not owned_alive():
        return {'signal': None, 'reason': 'No identified test process is alive'}
    try:
        killpg(pgid, signal.SIGTERM)
    except ProcessLookupError:
        return {'signal': None, 'reason': 'Test group already exited'}
    for _ in range(40):
        if not owned_alive():
            return {'signal': 'SIGTERM', 'identified_processes_exited': True}
        sleep(.25)
    if owned_alive():
        try:
            killpg(pgid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        return {'signal': 'SIGKILL', 'identified_processes_exited': False,
                'completion_requires_launcher_receipt': True}
    return {'signal': 'SIGTERM', 'identified_processes_exited': True}


def seconds(value):
    try:
        result = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError('Invalid measured duration') from exc
    if not result.is_finite() or result < 0:
        raise ValueError('Duration must be finite and nonnegative')
    return result


class PerformanceGuard:
    def __init__(self, phase_budgets, source_budgets):
        self.phase_budgets = self._budgets(phase_budgets)
        self.source_budgets = self._budgets(source_budgets)
        if not self.phase_budgets:
            raise ValueError('Acceptance requires explicit phase baselines')
        self.failure = None

    @staticmethod
    def _budgets(values):
        result = {}
        for key, value in values.items():
            duration = seconds(value)
            if duration == 0:
                raise ValueError('Baseline must be positive')
            result[key] = duration
        return result

    def observe(self, tag, event):
        if self.failure is not None:
            return self.failure
        if tag not in {'FULL_CHAIN_SOURCE', 'FULL_CHAIN_PROGRESS', 'FULL_CHAIN_TASK'}:
            return None
        phase = event['phase']
        if phase not in self.phase_budgets:
            raise ValueError('No accepted baseline for phase: ' + phase)
        if tag == 'FULL_CHAIN_SOURCE':
            key = (phase, event['sourceIndex'])
            budget = self.source_budgets.get(key)
            if budget is None:
                return None  # The explicit overall phase budget still applies.
            dimension = 'source'
        else:
            budget = self.phase_budgets[phase]
            dimension = 'phase'
        elapsed = seconds(event['seconds'])
        if elapsed > budget:
            self.failure = {
                'status': 'PERFORMANCE_REGRESSION',
                'phase': phase,
                'dimension': dimension,
                'source_index': event.get('sourceIndex') if dimension == 'source' else None,
                'baseline_seconds': str(budget),
                'observed_seconds': str(elapsed),
                'measurement_complete': tag != 'FULL_CHAIN_PROGRESS',
                'stop_remaining_acceptance': True,
                'trigger': tag,
            }
        return self.failure
