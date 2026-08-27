#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time


DEFAULT_DEVICES = 2000000
DEFAULT_SHIPMENTS = 4000000
DEFAULT_BATCH_SIZE = 10000
ID_BASE = 980000000000000000
PREFIX = "FAST001_PERF_"


def mysql(container: str, database: str, sql: str) -> None:
    command = [
        "docker", "exec", "-i", container, "sh", "-lc",
        'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"',
    ]
    result = subprocess.run(command, input=f"USE `{database}`;\n{sql}\n", text=True, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"mysql exited with {result.returncode}")


def device_values(start: int, end: int, tenant_id: int) -> str:
    values = []
    for ordinal in range(start, end):
        project_id = 100000 + ordinal % 10000
        customer_id = 200000 + ordinal % 20000
        status = "ACTIVE" if ordinal % 20 else "INACTIVE"
        sync_status = ("FRESH", "STALE", "FAILED", "PENDING_MAPPING", "NOT_AVAILABLE")[ordinal % 5]
        values.append(
            f"({ID_BASE + ordinal},'{PREFIX}SN_{ordinal:010d}','{PREFIX}DEVICE_{ordinal:010d}',"
            f"'{PREFIX}PRODUCT_{ordinal % 100:03d}','{PREFIX}MODEL_{ordinal % 1000:04d}',"
            f"{project_id},1,{customer_id},1,'{status}','FAST001_PERF_GENERATOR',"
            f"'{PREFIX}DEVICE_KEY_{ordinal:010d}','1','{sync_status}','fast001_perf','fast001_perf',b'0',{tenant_id})"
        )
    return ",\n".join(values)


def shipment_values(start: int, end: int, devices: int, tenant_id: int) -> str:
    values = []
    for ordinal in range(start, end):
        device_ordinal = ordinal % devices
        values.append(
            f"({ID_BASE + 100000000 + ordinal},'{PREFIX}SN_{device_ordinal:010d}',"
            f"TIMESTAMP('2026-01-01 00:00:00') + INTERVAL {ordinal % 31536000} SECOND,"
            f"'{PREFIX}PACKAGE_{ordinal:010d}','{PREFIX}CONTRACT_{ordinal % 50000:05d}',"
            f"'FAST001_PERF_SHIPMENT','FAST001_PERF_GENERATOR','{PREFIX}SHIPMENT_KEY_{ordinal:010d}',"
            f"'1','FRESH','fast001_perf','fast001_perf',b'0',{tenant_id})"
        )
    return ",\n".join(values)


def insert_devices(container: str, database: str, devices: int, tenant_id: int, batch_size: int) -> None:
    for start in range(0, devices, batch_size):
        end = min(start + batch_size, devices)
        sql = f"""
INSERT INTO `ast_device` (
  `id`, `sn`, `name`, `product_code`, `product_model`, `project_id`, `project_assignment_version`,
  `customer_id`, `customer_assignment_version`, `status`, `source_system`, `source_key`, `source_version`,
  `sync_status`, `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
{device_values(start, end, tenant_id)}
ON DUPLICATE KEY UPDATE
  `project_id` = VALUES(`project_id`),
  `customer_id` = VALUES(`customer_id`),
  `status` = VALUES(`status`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_perf',
  `deleted` = b'0';
"""
        mysql(container, database, sql)
        print(f"devices {end}/{devices}", file=sys.stderr)


def insert_shipments(container: str, database: str, shipments: int, devices: int, tenant_id: int, batch_size: int) -> None:
    for start in range(0, shipments, batch_size):
        end = min(start + batch_size, shipments)
        sql = f"""
INSERT INTO `ast_device_shipment` (
  `id`, `device_sn`, `shipment_time`, `package_no`, `contract_no`, `event_type`,
  `source_system`, `source_key`, `source_version`, `sync_status`, `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
{shipment_values(start, end, devices, tenant_id)}
ON DUPLICATE KEY UPDATE
  `shipment_time` = VALUES(`shipment_time`),
  `package_no` = VALUES(`package_no`),
  `contract_no` = VALUES(`contract_no`),
  `updater` = 'fast001_perf',
  `deleted` = b'0';
"""
        mysql(container, database, sql)
        print(f"shipments {end}/{shipments}", file=sys.stderr)


def cleanup(container: str, database: str, tenant_id: int) -> None:
    sql = f"""
DELETE FROM `ast_device_shipment`
WHERE `tenant_id` = {tenant_id}
  AND `source_key` LIKE '{PREFIX}%';
DELETE FROM `ast_device`
WHERE `tenant_id` = {tenant_id}
  AND `sn` LIKE '{PREFIX}%'
  AND `source_key` LIKE '{PREFIX}%';
"""
    mysql(container, database, sql)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", required=True)
    parser.add_argument("--database", default="npdms")
    parser.add_argument("--devices", type=int, default=DEFAULT_DEVICES)
    parser.add_argument("--shipments", type=int, default=DEFAULT_SHIPMENTS)
    parser.add_argument("--tenant-id", type=int, default=1)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--cleanup", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.devices <= 0 or args.shipments < 0 or args.batch_size <= 0 or args.tenant_id < 0:
        raise SystemExit("devices、shipments、batch-size 必须有效，tenant-id 不能为负数")
    started = time.perf_counter()
    if args.cleanup:
        cleanup(args.container, args.database, args.tenant_id)
        payload = {"cleanup": True, "tenantId": args.tenant_id, "prefix": PREFIX, "pass": True}
    else:
        insert_devices(args.container, args.database, args.devices, args.tenant_id, args.batch_size)
        insert_shipments(args.container, args.database, args.shipments, args.devices, args.tenant_id, args.batch_size)
        payload = {
            "cleanup": False,
            "devices": args.devices,
            "shipments": args.shipments,
            "tenantId": args.tenant_id,
            "batchSize": args.batch_size,
            "prefix": PREFIX,
            "elapsedSeconds": round(time.perf_counter() - started, 3),
            "pass": True,
        }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
