#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"

bash "$SCRIPT_DIR/bootstrap-production-deploy-iam-base.sh"
bash "$SCRIPT_DIR/bootstrap-production-controller-wif.sh"
bash "$SCRIPT_DIR/verify-production-controller-wif.sh"
