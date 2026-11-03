#!/usr/bin/env python3
"""Fail-closed API matrix planner for ARUNO Android CI; does not run tests."""
import argparse
import json
import os
import re
from pathlib import Path

SUPPORTED = {
    "dpc": (28, 29, 35, 36),
    "aru-tiker": (28, 35),
    "aru-click": (28, 35),
}
TIERS = ("lightweight", "targeted", "full")


def make_plan(app, tier, target_api=0, approval_reference="", approved_source_sha="",
              event_name=""):
    if app not in SUPPORTED:
        raise ValueError(f"Unknown application: {app}")
    if tier not in TIERS:
        raise ValueError(f"Unknown tier: {tier}")
    if tier == "lightweight":
        if target_api or approval_reference or approved_source_sha:
            raise ValueError("lightweight must not request emulator or release credentials")
        apis = []
    elif tier == "targeted":
        if target_api not in SUPPORTED[app]:
            raise ValueError(f"Select ONE supported Android API for {app}: {SUPPORTED[app]}")
        if approval_reference or approved_source_sha:
            raise ValueError("targeted cannot authorize a release")
        apis = [target_api]
    else:
        if target_api:
            raise ValueError("full always selects the complete supported matrix")
        if event_name in ("pull_request", "pull_request_target"):
            raise ValueError("full verification is prohibited on pull request events")
        if not approval_reference.strip():
            raise ValueError("full requires explicit release approval reference")
        if not re.fullmatch(r"[a-f0-9]{40}", approved_source_sha):
            raise ValueError("full requires a pinned 40-digit application source commit")
        apis = list(SUPPORTED[app])
    return {
        "application": app,
        "tier": tier,
        "matrix": {"include": [{"api": x} for x in apis]},
        "emulator_count": len(apis),
        "approved_source_sha": approved_source_sha if tier == "full" else "",
        "release_authorized_by_plan": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", required=True)
    parser.add_argument("--tier", required=True)
    parser.add_argument("--target-api", type=int, default=0)
    parser.add_argument("--approval-reference", default="")
    parser.add_argument("--approved-source-sha", default="")
    args = parser.parse_args()
    plan = make_plan(args.app, args.tier, args.target_api,
                     args.approval_reference, args.approved_source_sha,
                     os.environ.get("GITHUB_EVENT_NAME", ""))
    print(json.dumps(plan, ensure_ascii=False, separators=(",", ":")))
    if os.environ.get("GITHUB_OUTPUT"):
        # A plan is not evidence of emulator success or permission to publish.
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as out:
            out.write("matrix=" + json.dumps(plan["matrix"], separators=(",", ":")) + "\n")
            out.write("tier=" + plan["tier"] + "\n")
            out.write("emulator_count=" + str(plan["emulator_count"]) + "\n")


if __name__ == "__main__":
    main()
