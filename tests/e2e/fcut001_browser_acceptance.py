#!/usr/bin/env python3
"""F-CUT-001 isolated runtime and real-browser acceptance.

The script creates acceptance-only data through the existing aggregate API. It
does not read reference workbooks and does not create production seed data.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from playwright.sync_api import Page, sync_playwright


RISK_CATEGORIES = (
    "CURRENT_VERSION_BULLETIN", "TARGET_VERSION_BULLETIN",
    "DUAL_CONFIG_CONSISTENCY", "FILTER_NAT_QOS_COMPILE_COUNT",
    "COMPILE_LIMIT_ASSESSMENT", "SESSION_SYNC", "DUAL_CONTROLLER_VERSION",
    "PACKAGE_MD5", "MAJOR_PROJECT_SPARES", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
    "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
    "CONFIG_BACKUP", "DYNAMIC_TABLE_COLLECTION", "MTU_JUMBO_FRAME",
    "HUNDRED_G_FEC", "LONG_CONNECTION", "SECOND_PASS_DEVICE", "STP",
    "F5_DEFAULT", "ADWARE_DEFAULT", "ROOM_OPERATION_COMMITMENT",
)
ALL_SITUATION = {
    "CURRENT_VERSION_BULLETIN", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
    "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
    "CONFIG_BACKUP", "ROOM_OPERATION_COMMITMENT",
}
DUAL_COUNTS = {
    "VSM": 17,
    "SILENT_DUAL": 25,
    "DRP_DUAL": 23,
    "NORMAL_DUAL": 24,
    "CLUSTER": 8,
}
SURVEY_CATEGORIES = (
    "CUTOVER_BACKGROUND", "BUSINESS_SUMMARY", "IMPACT_SCOPE",
    "CONTINUITY_REQUIREMENT", "INTERRUPTION_COUNT", "CURRENT_TOPOLOGY",
    "DEVICE_LOCATION_PLAN", "INTERFACE_INTERCONNECT_PLAN", "IP_VLAN_PLAN",
    "PERFORMANCE_BASELINE", "CONNECTIVITY_TEST_CASE", "VENDOR_CONFIG_TRANSLATION",
)


class Api:
    def __init__(self, base_url: str, username: str, password: str) -> None:
        self.base_url = base_url.rstrip("/") + "/admin-api"
        self.tenant_id = "1"
        login = self.request(
            "POST",
            "/system/auth/login",
            {"username": username, "password": password, "captchaVerification": ""},
            authenticated=False,
        )
        self.token = login["accessToken"]

    def request(
        self,
        method: str,
        path: str,
        body: Any | None = None,
        *,
        headers: dict[str, str] | None = None,
        authenticated: bool = True,
    ) -> Any:
        request_headers = {"tenant-id": self.tenant_id, "Content-Type": "application/json"}
        if authenticated:
            request_headers["Authorization"] = f"Bearer {self.token}"
        if headers:
            request_headers.update(headers)
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path, data=data, headers=request_headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise AssertionError(f"{method} {path} returned HTTP {error.code}: {detail}") from error
        if payload.get("code") != 0:
            raise AssertionError(f"{method} {path} failed: {payload.get('code')} {payload.get('msg')}")
        return payload.get("data")

    def detail(self, revision_id: str | int) -> dict[str, Any]:
        return self.request("GET", f"/api/v1/pms/cutover-config/revisions/{revision_id}")

    def update(self, revision: dict[str, Any]) -> dict[str, Any]:
        revision_id = revision["id"]
        self.request(
            "PUT",
            f"/api/v1/pms/cutover-config/revisions/{revision_id}",
            revision,
            headers={"If-Match": str(revision["version"])},
        )
        return self.detail(revision_id)

    def validate(self, revision_id: str | int) -> dict[str, Any]:
        return self.request(
            "POST", f"/api/v1/pms/cutover-config/revisions/{revision_id}/actions/validate"
        )


def item(key: str, item_type: str, category: str, sort_order: int, *, subtable: str | None = None,
         schema: dict[str, Any] | None = None) -> dict[str, Any]:
    return {
        "stableItemKey": key,
        "itemType": item_type,
        "businessCategoryCode": category,
        "itemName": f"F-CUT-001 隔离验收 · {key}",
        "itemDescription": "仅用于本次隔离浏览器验收",
        "interfaceFormat": "TABLE",
        "interfaceSchema": schema or {},
        "feedbackFormat": "BOOLEAN_REMARK",
        "required": True,
        "workMode": "MANUAL",
        "externalSourceConfig": None,
        "subtableCode": subtable,
        "enabled": True,
        "sortOrder": sort_order,
    }


def rule(key: str, item_key: str, conditions: dict[str, list[str]], required: bool) -> dict[str, Any]:
    return {
        "stableRuleKey": key,
        "stableItemKey": item_key,
        "dimensionConditions": conditions,
        "priority": 10,
        "requiredResult": required,
        "enabled": True,
    }


def background_schema() -> dict[str, Any]:
    return {"fields": [
        {"code": "solvesOnlineIssue"},
        {"code": "issueTicketNo", "visibleWhen": {"field": "solvesOnlineIssue", "equals": True}},
        {"code": "issueHandler", "visibleWhen": {"field": "solvesOnlineIssue", "equals": True}},
        {"code": "repeatCutover"},
        {"code": "firstCutoverOwner", "visibleWhen": {"field": "repeatCutover", "equals": True}},
        {"code": "backgroundDescription"},
    ]}


def enabled_dictionary_values(api: Api) -> dict[str, list[str]]:
    rows = api.request("GET", "/system/dict-data/simple-list")
    result: dict[str, list[str]] = {}
    for dictionary_type in (
        "pms_cutover_type", "pms_network_mode", "pms_device_type", "pms_risk_level"
    ):
        result[dictionary_type] = list(dict.fromkeys(
            str(row["value"]) for row in rows if row["dictType"] == dictionary_type
        ))
    return result


def complete_revision(api: Api) -> tuple[dict[str, Any], str | int]:
    page = api.request("GET", "/api/v1/pms/cutover-config/revisions?pageNo=1&pageSize=100")
    published = [row for row in page["list"] if row["statusCode"] == "PUBLISHED"]
    assert published, "No published cutover configuration exists"
    source = max(published, key=lambda row: row["revisionNo"])
    draft_id = api.request(
        "POST",
        f"/api/v1/pms/cutover-config/revisions/{source['id']}/actions/copy",
        headers={"If-Match": str(source["version"])},
    )
    draft = api.detail(draft_id)
    dictionaries = enabled_dictionary_values(api)
    cutover_types = dictionaries["pms_cutover_type"]
    device_types = dictionaries["pms_device_type"]
    levels = [level for level in dictionaries["pms_risk_level"] if level in {"A", "B", "C"}]
    assert "VERSION_UPGRADE" in cutover_types
    assert set(DUAL_COUNTS).issubset(dictionaries["pms_network_mode"])
    assert set(levels) == {"A", "B", "C"}

    items: list[dict[str, Any]] = []
    rules: list[dict[str, Any]] = []
    sort_order = 10
    for category in RISK_CATEGORIES:
        key = f"RISK_{category}"
        items.append(item(key, "RISK", category, sort_order))
        sort_order += 10
        if category in ALL_SITUATION:
            conditions = {
                "CUTOVER_TYPE": cutover_types,
                "DEVICE_TYPE": device_types,
                "CUTOVER_LEVEL": levels,
            }
            required = True
        elif category == "TARGET_VERSION_BULLETIN":
            conditions = {"CUTOVER_TYPE": ["VERSION_UPGRADE"]}
            required = True
        else:
            conditions = {"CUTOVER_LEVEL": levels}
            required = False
        rules.append(rule(f"RULE_{key}", key, conditions, required))

    for mode, count in DUAL_COUNTS.items():
        for index in range(1, count + 1):
            key = f"DUAL_{mode}_{index:03d}"
            items.append(item(key, "DUAL_MACHINE_CHECK", mode, sort_order, subtable=mode))
            rules.append(rule(f"RULE_{key}", key, {"NETWORK_MODE": [mode]}, True))
            sort_order += 10

    for category in SURVEY_CATEGORIES:
        key = f"SURVEY_{category}"
        schema = background_schema() if category == "CUTOVER_BACKGROUND" else {}
        items.append(item(key, "BUSINESS_SURVEY", category, sort_order, schema=schema))
        rules.append(rule(f"RULE_{key}", key, {"CUTOVER_LEVEL": levels}, True))
        sort_order += 10

    draft.update({
        "configurationName": "F-CUT-001 隔离浏览器验收修订",
        "changeSummary": "隔离验收数据：24类普通风险、五类双机97项、12类调研；不作为生产种子",
        "items": items,
        "bindingRules": rules,
    })
    draft = api.update(draft)
    validation = api.validate(draft_id)
    assert validation["valid"], validation["errors"]
    return draft, source["id"]


def open_draft(page: Page, base_url: str, revision_no: int) -> None:
    page.goto(base_url.rstrip("/") + "/pms/cutover/cutover-config")
    page.wait_for_load_state("networkidle")
    row = page.locator(".el-table__body tr").filter(has_text=f"F-CUT-001 隔离浏览器验收修订").filter(
        has_text=str(revision_no)
    ).first
    if row.count() == 0:
        body = page.locator("body").inner_text()[:2000]
        raise AssertionError(f"draft row not found at {page.url}; body={body}")
    row.get_by_text("编辑", exact=True).click()
    page.get_by_role("dialog").wait_for(state="visible")


def ui_login(page: Page, base_url: str, username: str, password: str) -> None:
    page.goto(base_url)
    page.wait_for_load_state("networkidle")
    inputs = page.locator(".login-form input")
    inputs.nth(0).fill(username)
    inputs.nth(1).fill(password)
    page.locator(".login-form button.el-button--primary").first.click()
    page.wait_for_url(lambda url: "/login" not in url, timeout=30_000)
    page.wait_for_load_state("networkidle")


def assert_validation(api: Api, revision: dict[str, Any], expected: str) -> None:
    revision = api.update(revision)
    result = api.validate(revision["id"])
    messages = [error["message"] for error in result["errors"]]
    assert not result["valid"] and any(expected in message for message in messages), messages


def browser_acceptance(
    api: Api,
    base_url: str,
    username: str,
    password: str,
    chromium_path: str,
    output_dir: Path,
    draft: dict[str, Any],
    source_published_id: str | int,
) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    console_errors: list[str] = []
    request_failures: list[str] = []
    checks: list[str] = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, executable_path=chromium_path)
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        page = context.new_page()
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("requestfailed", lambda request: request_failures.append(
            f"{request.method} {request.url}: {request.failure}"
        ))
        ui_login(page, base_url, username, password)
        open_draft(page, base_url, draft["revisionNo"])
        page.get_by_role("tab", name="风险矩阵").click()
        page.get_by_text("五类双机检查 97/97 项", exact=True).wait_for()
        page.get_by_text("普通风险类别（24/24）", exact=True).wait_for()
        page.screenshot(path=output_dir / "01-complete-risk-matrix-1440.png", full_page=True)
        checks.append("risk matrix displays 24 categories and five-mode total 97/97")

        page.get_by_text("VSM双机：17/17 项", exact=True).click()
        vsm_row = page.locator("#pane-risk .el-table__body tr:visible").filter(
            has_text="DUAL_VSM_001"
        ).first
        vsm_row.locator(".el-switch").click()
        page.get_by_text("五类双机检查 96/97 项", exact=True).wait_for()
        page.get_by_role("button", name="保存草稿").click()
        page.get_by_text("草稿已保存", exact=True).wait_for()
        page.get_by_role("button", name="发布预检").click()
        page.get_by_text("发布预检发现", exact=False).wait_for()
        page.get_by_role("tab", name="风险矩阵").click()
        page.locator(".el-dialog:visible").get_by_text(
            "VSM双机应为17项，当前16项", exact=False
        ).first.wait_for()
        page.screenshot(path=output_dir / "02-dual-count-rejected.png", full_page=True)
        source_status = api.detail(source_published_id)["statusCode"]
        assert source_status == "PUBLISHED", source_status
        checks.append("16/17 VSM is rejected while the old published revision remains published")

        vsm_row = page.locator("#pane-risk .el-table__body tr:visible").filter(
            has_text="DUAL_VSM_001"
        ).first
        vsm_row.locator(".el-switch").click()
        page.get_by_text("五类双机检查 97/97 项", exact=True).wait_for()
        page.get_by_role("button", name="关闭").click()
        draft = api.detail(draft["id"])
        vsm_item = next(item for item in draft["items"] if item["stableItemKey"] == "DUAL_VSM_001")
        vsm_item["enabled"] = True
        draft = api.update(draft)
        assert api.validate(draft["id"])["valid"]

        coverage_rule = next(rule for rule in draft["bindingRules"]
                             if rule["stableRuleKey"] == "RULE_RISK_CURRENT_VERSION_BULLETIN")
        original_devices = coverage_rule["dimensionConditions"]["DEVICE_TYPE"]
        coverage_rule["dimensionConditions"]["DEVICE_TYPE"] = original_devices[:-1]
        assert_validation(api, draft, "缺少显式覆盖")
        draft = api.detail(draft["id"])
        open_draft(page, base_url, draft["revisionNo"])
        page.get_by_role("button", name="发布预检").click()
        page.get_by_text("发布预检发现", exact=False).wait_for()
        page.get_by_role("tab", name="风险矩阵").click()
        page.locator(".el-dialog:visible").get_by_text("缺少显式覆盖", exact=False).first.wait_for()
        page.screenshot(path=output_dir / "03-coverage-gap-rejected.png", full_page=True)
        page.get_by_role("button", name="关闭").click()
        checks.append("an all-situation device coverage gap is rejected and located in the risk matrix")

        draft = api.detail(draft["id"])
        coverage_rule = next(rule for rule in draft["bindingRules"]
                             if rule["stableRuleKey"] == "RULE_RISK_CURRENT_VERSION_BULLETIN")
        coverage_rule["dimensionConditions"]["DEVICE_TYPE"] = original_devices
        draft = api.update(draft)
        survey_rule = next(rule for rule in draft["bindingRules"]
                           if rule["stableRuleKey"] == "RULE_SURVEY_CUTOVER_BACKGROUND")
        survey_rule["requiredResult"] = None
        assert_validation(api, draft, "调研绑定的必填结果不能为空")
        draft = api.detail(draft["id"])
        open_draft(page, base_url, draft["revisionNo"])
        page.get_by_role("tab", name="调研矩阵").click()
        page.get_by_text("核心类别 12/12", exact=True).wait_for()
        page.get_by_role("button", name="发布预检").click()
        page.get_by_text("发布预检发现", exact=False).wait_for()
        page.get_by_role("tab", name="调研矩阵").click()
        page.locator(".el-dialog:visible").get_by_text(
            "调研绑定的必填结果不能为空", exact=False
        ).first.wait_for()
        page.screenshot(path=output_dir / "04-survey-required-rejected.png", full_page=True)
        page.get_by_role("button", name="关闭").click()
        checks.append("a null survey required-result is rejected and displayed in the survey matrix")

        draft = api.detail(draft["id"])
        survey_rule = next(rule for rule in draft["bindingRules"]
                           if rule["stableRuleKey"] == "RULE_SURVEY_CUTOVER_BACKGROUND")
        survey_rule["requiredResult"] = True
        background = next(item for item in draft["items"]
                          if item["stableItemKey"] == "SURVEY_CUTOVER_BACKGROUND")
        issue_ticket = next(field for field in background["interfaceSchema"]["fields"]
                            if field["code"] == "issueTicketNo")
        issue_ticket["visibleWhen"]["field"] = "repeatCutover"
        assert_validation(api, draft, "issueTicketNo必须依赖solvesOnlineIssue == true")
        draft = api.detail(draft["id"])
        open_draft(page, base_url, draft["revisionNo"])
        page.get_by_role("tab", name="调研矩阵").click()
        page.get_by_role("button", name="发布预检").click()
        page.get_by_text("发布预检发现", exact=False).wait_for()
        page.get_by_role("tab", name="调研矩阵").click()
        page.locator(".el-dialog:visible").get_by_text(
            "issueTicketNo必须依赖solvesOnlineIssue == true", exact=False
        ).first.wait_for()
        page.screenshot(path=output_dir / "05-background-dependency-rejected.png", full_page=True)
        page.get_by_role("button", name="关闭").click()
        checks.append("a broken survey background dependency is rejected and displayed")

        draft = api.detail(draft["id"])
        background = next(item for item in draft["items"]
                          if item["stableItemKey"] == "SURVEY_CUTOVER_BACKGROUND")
        background["interfaceSchema"] = background_schema()
        draft = api.update(draft)
        assert api.validate(draft["id"])["valid"]

        page.reload()
        page.wait_for_load_state("networkidle")
        row = page.locator(".el-table__body tr").filter(
            has_text="F-CUT-001 隔离浏览器验收修订"
        ).filter(has_text=str(draft["revisionNo"])).first
        row.get_by_text("发布", exact=True).click()
        page.get_by_role("dialog", name="提示").get_by_role("button", name="确定").click()
        page.get_by_text("配置已发布", exact=True).wait_for()
        published = api.detail(draft["id"])
        assert published["statusCode"] == "PUBLISHED"
        checks.append("the repaired revision is published through the UI")

        for width in (320, 768, 1024, 1440):
            page.set_viewport_size({"width": width, "height": 900})
            row = page.locator(".el-table__body tr").filter(
                has_text="F-CUT-001 隔离浏览器验收修订"
            ).filter(has_text=str(draft["revisionNo"])).first
            row.get_by_text("查看", exact=True).click()
            page.get_by_text("已发布或已停用修订为只读", exact=False).wait_for()
            page.get_by_role("tab", name="风险矩阵").click()
            page.get_by_text("五类双机检查 97/97 项", exact=True).wait_for()
            page.screenshot(path=output_dir / f"06-published-readonly-{width}.png", full_page=True)
            page.get_by_role("button", name="关闭").click()
        checks.append("published history is readonly at 320/768/1024/1440 widths")
        browser.close()

    assert not console_errors, console_errors
    assert not request_failures, request_failures
    return {
        "status": "PASS",
        "checks": checks,
        "consoleErrors": console_errors,
        "requestFailures": request_failures,
        "publishedRevisionId": str(draft["id"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:20082")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--chromium", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    password = os.environ.get("NPDMS_ACCEPTANCE_PASSWORD")
    if not password:
        raise SystemExit("NPDMS_ACCEPTANCE_PASSWORD is required")
    api = Api(args.base_url.replace(":20082", ":61280"), args.username, password)
    draft, source_published_id = complete_revision(api)
    result = browser_acceptance(
        api, args.base_url, args.username, password, args.chromium,
        args.output, draft, source_published_id,
    )
    result_path = args.output / "result.json"
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
