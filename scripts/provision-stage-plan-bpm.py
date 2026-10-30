"""Register the stage-plan review form using normal BPM model APIs.

Supply NPDMS_BPM_ADMIN_TOKEN through the environment (never a command argument).
Requires the existing model manager's create/update/deploy permissions. Existing
instances and published definitions are preserved; deployment creates a version.
"""
import argparse
import json
import os
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

KEY = "pms-sol-stage-plan"
VIEW = "/pms/engineering/stage-plan/ApprovalDetail"


def provision(api, manager_id, category):
    definition = api("GET", "/bpm/process-definition/get?key=" + KEY)
    if definition and definition.get("formCustomViewPath") == VIEW and definition.get("formType") == 20:
        return {"definitionId": definition["id"], "changed": False}
    models = [item for item in api("GET", "/bpm/model/list") if item["key"] == KEY]
    if len(models) > 1:
        raise ValueError("Multiple stage-plan models; resolve the model identity first")
    xml = (Path(__file__).resolve().parents[1] / "pms-module-engineering/src/main/resources/processes/pms-sol-stage-plan.bpmn20.xml").read_text(encoding="utf-8")
    if models:
        model = api("GET", "/bpm/model/get?id=" + models[0]["id"])
        root = ET.fromstring(model["bpmnXml"])
        tasks = root.findall(".//{http://www.omg.org/spec/BPMN/20100524/MODEL}userTask")
        if len(tasks) != 1 or tasks[0].get("id") != "serviceManagerApprove" or tasks[0].get("{http://flowable.org/bpmn}candidateStrategy") != "35":
            raise ValueError("Existing workflow differs; do not overwrite its approval policy")
        # Keep the existing workflow and managers; only attach the business snapshot view.
        model_id = model["id"]
    else:
        model = dict(key=KEY, name="阶段施工计划审批", category=category, type=10,
                     managerUserIds=[manager_id], bpmnXml=xml, autoApprovalType=0,
                     allowCancelRunningProcess=True, allowWithdrawTask=False)
        model_id = None
    model.update(formType=20, formCustomViewPath=VIEW,
                 formCustomCreatePath=VIEW, visible=False)
    if model_id:
        api("PUT", "/bpm/model/update", model)
    else:
        model_id = api("POST", "/bpm/model/create", model)
    api("POST", "/bpm/model/deploy?id=" + model_id)
    deployed = api("GET", "/bpm/process-definition/get?key=" + KEY)
    if not deployed or deployed.get("formCustomViewPath") != VIEW:
        raise RuntimeError("Published definition did not register the stage-plan view")
    return {"definitionId": deployed["id"], "changed": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="Backend admin-api URL")
    parser.add_argument("--tenant-id", required=True, type=int)
    parser.add_argument("--manager-id", required=True, type=int)
    parser.add_argument("--category", required=True)
    args = parser.parse_args()
    token = os.environ["NPDMS_BPM_ADMIN_TOKEN"]

    def api(method, path, body=None):
        request = urllib.request.Request(args.base_url.rstrip("/") + path,
            data=None if body is None else json.dumps(body).encode(), method=method,
            headers={"Content-Type": "application/json", "tenant-id": str(args.tenant_id),
                     "Authorization": "Bearer " + token})
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.load(response)
        if result.get("code") != 0:
            raise RuntimeError(result.get("msg", "BPM operation failed"))
        return result.get("data")

    print(json.dumps(provision(api, args.manager_id, args.category)))
