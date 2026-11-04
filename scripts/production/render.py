"""Render non-secret four-host deployment files; never connects to a server."""
import argparse
import json
import re
from pathlib import Path

IPS = {"fe1": "172.20.19.2", "fe2": "172.20.20.2", "be": "172.17.73.104", "db": "172.17.74.19"}


def render(config, destination):
    for key in ("internal_domain", "external_domain", "backend_tls_name", "db_tls_name"):
        if not re.fullmatch(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?", config[key]) or ".." in config[key]:
            raise ValueError(f"Invalid or missing DNS name: {key}")
    if config["internal_domain"] == config["external_domain"]:
        raise ValueError("Internal and external domains must differ")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", config["db_name"]):
        raise ValueError("Invalid database name")
    required = {"internal_domain", "external_domain", "backend_tls_name", "db_tls_name", "db_name"}
    if not required <= set(config) or set(config) - required - {"minio_bucket"}:
        raise ValueError("Only native deployment DNS/database parameters are accepted")
    bucket = config.get("minio_bucket", "sdp")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,61}[a-z0-9]", bucket):
        raise ValueError("Invalid MinIO bucket")
    values = {**config, "minio_bucket": bucket, **{f"{k}_ip": v for k, v in IPS.items()}}
    templates = Path(__file__).parent / "templates"
    if destination.exists():
        raise ValueError("Output already exists; use a new configuration version")
    outputs = {}
    for source in templates.rglob("*.in"):
        content = source.read_text(encoding="utf-8")
        for key, value in values.items():
            content = content.replace("@@" + key + "@@", value)
        if re.search(r"@@\w+@@", content):
            raise ValueError(f"Unresolved placeholder in {source.name}")
        outputs[source.relative_to(templates).with_suffix("")] = content
    for relative, content in outputs.items():
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8", newline="\n")
    print(f"Rendered {len(outputs)} non-secret files to {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    render(json.loads(args.config.read_text(encoding="utf-8-sig")), args.destination)
