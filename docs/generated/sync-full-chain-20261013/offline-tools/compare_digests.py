"""Compare existing phase-boundary digest snapshots, without querying a database.

Cumulative timer values include streaming-consumer backpressure. Boundaries are
non-atomic: this report is diagnostic evidence, not additive wall-clock time.
"""
import argparse
import gzip
import json
from pathlib import Path

FIELDS = ('count', 'timer_ps', 'lock_ps', 'rows_sent', 'rows_affected',
          'rows_examined', 'sort_rows', 'tmp_tables', 'tmp_disk_tables', 'no_index_used')


def read(path):
    with gzip.open(path, 'rt', encoding='utf-8') as stream:
        rows = [json.loads(line) for line in stream if line.strip()]
    result = {}
    for row in rows:
        key = (row.get('schema'), row['digest'])
        if key in result:
            raise ValueError('Duplicate schema/digest in ' + str(path))
        result[key] = row
    return result


def delta(start, end, target, source):
    before, after = read(start), read(end)
    output, warnings = {}, []
    for key, row in after.items():
        old = before.get(key, {})
        values = {field: int(row.get(field) or 0) - int(old.get(field) or 0)
                  for field in FIELDS}
        if any(value < 0 for value in values.values()):
            warnings.append({'schema': key[0], 'digest': key[1], 'negative_delta': values})
            continue
        if not any(values.values()):
            continue
        schema = row.get('schema')
        scope = 'target' if schema == target else 'source' if schema == source else 'other'
        sql = row.get('normalized_sql') or ''
        # Only fixture schema/helper naming is normalized for offline comparison.
        # This does not change production business matching keys.
        sql = sql.replace(target, '<target>').replace(source, '<source>')
        sql = sql.replace('reuse_reusev154m_', 'reuse_<run>_').replace('reuse_reusev74m_', 'reuse_<run>_')
        aggregate_key = (scope, sql)
        item = output.setdefault(aggregate_key, {'scope': scope, 'normalized_sql': sql,
                                                 **{field: 0 for field in FIELDS}})
        for field in FIELDS:
            item[field] += values[field]
    missing = [{'schema': key[0], 'digest': key[1]} for key in before if key not in after]
    return output, {'negative_deltas': warnings, 'missing_end_digests': missing}


def compare(baseline, candidate):
    rows = []
    for key in baseline.keys() | candidate.keys():
        base, current = baseline.get(key), candidate.get(key)
        b = base or {field: 0 for field in FIELDS}
        c = current or {field: 0 for field in FIELDS}
        rows.append({'scope': key[0], 'normalized_sql': key[1],
                     'baseline': {field: b[field] for field in FIELDS},
                     'candidate': {field: c[field] for field in FIELDS},
                     'count_ratio': c['count'] / b['count'] if b['count'] else None,
                     'timer_ratio': c['timer_ps'] / b['timer_ps'] if b['timer_ps'] else None,
                     'baseline_seconds': b['timer_ps'] / 1e12,
                     'candidate_seconds': c['timer_ps'] / 1e12})
    rows.sort(key=lambda r: (-r['candidate']['timer_ps'], r['scope'], r['normalized_sql']))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--baseline-target', required=True)
    parser.add_argument('--candidate-target', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    reports = []
    for root, target in [(args.baseline, args.baseline_target), (args.candidate, args.candidate_target)]:
        reports.append(delta(root / 'initial_migration-start/digest.jsonl.gz',
                             root / 'initial_migration-end/digest.jsonl.gz', target, args.source))
    rows = compare(reports[0][0], reports[1][0])
    result = {'boundary': 'non_atomic', 'timers_are_not_additive_wall_clock': True,
              'streaming_source_select_includes_consumer_backpressure': True,
              'baseline_warnings': reports[0][1], 'candidate_warnings': reports[1][1],
              'rows': rows}
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    compact = [{k: v for k, v in row.items() if k != 'normalized_sql'} |
               {'normalized_sql': row['normalized_sql'][:1000]} for row in rows[:20]]
    print(json.dumps({**{k: v for k, v in result.items() if k != 'rows'},
                      'top20': compact, 'full_output': str(args.output)}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
