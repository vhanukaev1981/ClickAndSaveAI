#!/usr/bin/env bash
set -euo pipefail

P=click-save-ai-production
N=991489557172
DEPLOY="clickandsaveai-github-deployer@$P.iam.gserviceaccount.com"
V1_RUNTIME_SA="clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com"
V2_RUNTIME_SA="clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com"
V1_RUNTIME_ROLE="roles/datastore.user"
BUILD_DEFERRED_STATUS="DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION"
CLOUD_BUILD_SERVICE="cloudbuild.googleapis.com"
POOL=github-actions
PROVIDER=clickandsaveai-production
REGION=europe-west1
WIF_PROVIDER="projects/$N/locations/global/workloadIdentityPools/$POOL/providers/$PROVIDER"
RID=1314210715
OID=64756523
ENV=production
REF=refs/heads/main
WF='vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main'
ISSUER=https://token.actions.githubusercontent.com
MAPPING='google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref'
CONDITION="attribute.repository_id=='$RID' && attribute.repository_owner_id=='$OID' && attribute.environment=='$ENV' && attribute.ref=='$REF' && attribute.workflow_ref=='$WF'"
WIF_MEMBER="principalSet://iam.googleapis.com/projects/$N/locations/global/workloadIdentityPools/$POOL/attribute.repository_id/$RID"
REPO=vhanukaev1981/ClickAndSaveAI
PROJECT_ID="${PROJECT_ID:-$P}"
ALLOW_MISSING_ACTAS="${ALLOW_MISSING_ACTAS:-0}"
ALLOW_RUNTIME_BOOTSTRAP_GAP="${ALLOW_RUNTIME_BOOTSTRAP_GAP:-0}"
DISCOVERY_OUTPUT="${DISCOVERY_OUTPUT:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ROLES=(roles/cloudfunctions.developer roles/firebaserules.admin roles/datastore.indexAdmin roles/serviceusage.serviceUsageConsumer)
BAD_ROLES=(roles/owner roles/editor roles/firebase.admin roles/firebase.developAdmin roles/cloudfunctions.admin roles/run.admin roles/resourcemanager.projectIamAdmin roles/iam.serviceAccountAdmin roles/iam.serviceAccountTokenCreator roles/iam.serviceAccountUser roles/secretmanager.admin roles/secretmanager.secretAccessor roles/storage.admin roles/artifactregistry.admin)

pass(){ printf 'PASS  %s\n' "$*"; }
fatal(){ printf 'FAIL  %s\n' "$*" >&2; exit 1; }
contains(){ local n="$1" x; shift; for x in "$@"; do [[ "$x" == "$n" ]] && return 0; done; return 1; }
add_unique(){ local v="$1" n="$2" x; local -n a="$n"; for x in "${a[@]:-}"; do [[ "$x" == "$v" ]] && return; done; a+=("$v"); }
roles_for(){ gcloud projects get-iam-policy "$PROJECT_ID" --flatten='bindings[].members' --filter="bindings.members=serviceAccount:$1" --format='value(bindings.role)' 2>/dev/null | sed '/^$/d' | sort -u; }
has_actas(){ gcloud iam service-accounts get-iam-policy "$1" --project="$PROJECT_ID" --flatten='bindings[].members' --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:$DEPLOY" --format='value(bindings.members)' 2>/dev/null | grep -Fqx "serviceAccount:$DEPLOY"; }
valid_sa(){
  local s="$1"
  [[ -n "$s" && "$s" != *clickandsaveai-staging* && "$s" != *@clickandsaveai.* ]] || fatal "invalid Production service account: $s"
  case "$s" in
    "$V1_RUNTIME_SA"|"$V2_RUNTIME_SA"|"$N@cloudbuild.gserviceaccount.com"|"$N-compute@developer.gserviceaccount.com"|"service-$N@gcp-sa-cloudbuild.iam.gserviceaccount.com"|*"@$P.iam.gserviceaccount.com") : ;;
    *) fatal "identity is not owned by Production: $s" ;;
  esac
}

case "$PROJECT_ID" in clickandsaveai|clickandsaveai-staging) fatal "forbidden non-Production project: $PROJECT_ID";; esac
[[ "$PROJECT_ID" == "$P" ]] || fatal "PROJECT_ID must be exactly $P"
[[ "$ALLOW_MISSING_ACTAS" =~ ^[01]$ ]] || fatal 'ALLOW_MISSING_ACTAS must be 0 or 1'
[[ "$ALLOW_RUNTIME_BOOTSTRAP_GAP" =~ ^[01]$ ]] || fatal 'ALLOW_RUNTIME_BOOTSTRAP_GAP must be 0 or 1'
for c in gcloud python3 curl; do command -v "$c" >/dev/null || fatal "$c is required"; done
[[ -n "$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -1)" ]] || fatal 'no active gcloud account'
PID="$(gcloud projects describe "$P" --format='value(projectId)' 2>/dev/null || true)"
PNUM="$(gcloud projects describe "$P" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PID" == "$P" ]] || fatal "Project ID mismatch: ${PID:-missing}"
[[ "$PNUM" == "$N" ]] || fatal "Project Number mismatch: ${PNUM:-missing}"
pass 'Project ID/Number exact'
[[ "$(gcloud iam service-accounts describe "$DEPLOY" --project="$P" --format='value(email)' 2>/dev/null || true)" == "$DEPLOY" ]] || fatal 'deploy SA mismatch or missing'
pass 'deploy SA exact'
KC="$(gcloud iam service-accounts keys list --iam-account="$DEPLOY" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^$/d' | wc -l | tr -d '[:space:]')"
[[ "$KC" == 0 ]] || fatal "deploy SA has user-managed keys: $KC"
pass 'user-managed deploy-SA key count = 0'

mapfile -t PIDS < <(gcloud iam workload-identity-pools providers list --project="$P" --location=global --workload-identity-pool="$POOL" --format='value(name)' 2>/dev/null | sed '/^$/d;s#^.*/##')
[[ ${#PIDS[@]} -eq 1 && "${PIDS[0]}" == "$PROVIDER" ]] || fatal 'Production WIF provider inventory mismatch'
PJ="$(gcloud iam workload-identity-pools providers describe "$PROVIDER" --project="$P" --location=global --workload-identity-pool="$POOL" --format=json 2>/dev/null || true)"
[[ -n "$PJ" ]] || fatal 'Production WIF provider missing'
PJ="$PJ" python3 - "$WIF_PROVIDER" "$ISSUER" "$MAPPING" "$CONDITION" <<'PY' || fatal 'Production WIF provider boundary mismatch'
import json,os,sys
n,i,m,c=sys.argv[1:]
p=json.loads(os.environ['PJ'])
mm=dict(x.split('=',1) for x in m.split(','))
assert p.get('name')==n
assert str(p.get('disabled',False)).lower()=='false'
assert p.get('oidc',{}).get('issuerUri')==i
assert p.get('attributeMapping',{})==mm
assert p.get('attributeCondition','')==c
PY
pass 'Production WIF provider exact and enabled'
mapfile -t WM < <(gcloud iam service-accounts get-iam-policy "$DEPLOY" --project="$P" --flatten='bindings[].members' --filter='bindings.role=roles/iam.workloadIdentityUser' --format='value(bindings.members)' 2>/dev/null | sed '/^$/d' | sort -u)
[[ ${#WM[@]} -eq 1 && "${WM[0]}" == "$WIF_MEMBER" ]] || fatal 'Production WIF impersonation boundary mismatch'
pass 'Production WIF impersonation boundary exact'
mapfile -t DR < <(roles_for "$DEPLOY")
mapfile -t ER < <(printf '%s\n' "${DEPLOY_ROLES[@]}" | sort -u)
[[ "$(printf '%s\n' "${DR[@]}")" == "$(printf '%s\n' "${ER[@]}")" ]] || fatal 'deploy-SA project roles do not exactly match Block 3B.2B'
pass 'deploy-SA project roles exactly match Block 3B.2B'
POL="$(gcloud projects get-iam-policy "$P" --format=json 2>/dev/null || true)"
[[ -n "$POL" ]] || fatal 'project IAM policy unreadable'
POL="$POL" python3 - <<'PY' || fatal 'project-wide roles/iam.serviceAccountUser exists'
import json,os
p=json.loads(os.environ['POL'])
assert not any(b.get('role')=='roles/iam.serviceAccountUser' and b.get('members') for b in p.get('bindings',[]))
PY
[[ "$POL" != *clickandsaveai-staging* && "$POL" != *'serviceAccount:clickandsaveai-github-deployer@clickandsaveai.iam.gserviceaccount.com'* ]] || fatal 'staging/legacy deploy principal reference present'
pass 'no project-wide SA User; staging/legacy deploy principal references absent'

A="$(python3 - "$ROOT" "$P" "$V1_RUNTIME_SA" "$V2_RUNTIME_SA" <<'PY'
from pathlib import Path
import json,re,sys
r=Path(sys.argv[1]).resolve(); prod=sys.argv[2]; v1sa=sys.argv[3]; v2sa=sys.argv[4]
f=json.loads((r/'firebase.json').read_text()); fn=f.get('functions',{})
assert isinstance(fn,dict) and fn.get('source')=='functions' and fn.get('codebase')=='default' and fn.get('runtime')=='nodejs22'
def bad(v):
 if isinstance(v,dict): return any(re.sub('[^a-z]','',str(k).lower()) in {'serviceaccount','serviceaccountemail','buildserviceaccount'} or bad(x) for k,x in v.items())
 if isinstance(v,list): return any(map(bad,v))
 return False
assert not bad(f),'global Firebase service-account configuration present'
pkg=json.loads((r/'functions/package.json').read_text()); main=pkg.get('main'); assert main=='src/entry.js'
entry=(r/'functions'/main).resolve(); src=(r/'functions/src').resolve(); assert entry.is_file() and (entry.parent==src or src in entry.parents)
IR=re.compile(r'''(?:require\s*\(\s*|\bfrom\s*)["'](firebase-functions(?:/[^"']+)?)['"]\s*\)?''')
def cl(s):
 if s=='firebase-functions/v1' or s.startswith('firebase-functions/v1/'): return 'v1'
 if s=='firebase-functions/v2' or s.startswith('firebase-functions/v2/'): return 'v2'
 return 'neutral'
t=entry.read_text(errors='replace'); binds={}; ordered=[]
for n,q in re.findall(r'''\bconst\s+([\w$]+)\s*=\s*require\s*\(\s*["'](\./[^"']+)["']\s*\)''',t):
 p=(entry.parent/q); p=p if p.suffix else p.with_suffix('.js'); binds[n]=p.resolve(); ordered.append((n,q,p.resolve()))
assert ordered and ordered[0][1]=='./index','index must be the first local module required by entry.js'
m=re.search(r'module\.exports\s*=\s*\{(.*?)\}\s*;',t,re.S); assert m
names=re.findall(r'\.\.\.\s*([\w$]+)',m.group(1)); assert names
deployed=[]
for n in names:
 assert n in binds and binds[n].is_file(); rel=binds[n].relative_to(r).as_posix()
 if rel not in deployed: deployed.append(rel)
if re.search(r'\bexports\.[\w$]+\s*=|\bmodule\.exports\.[\w$]+\s*=',t):
 rel=entry.relative_to(r).as_posix()
 if rel not in deployed: deployed.append(rel)
g=set(); v1files=[]; v2files=[]
for rel in deployed:
 x=(r/rel).read_text(errors='replace')
 e={cl(s) for s in IR.findall(x)}&{'v1','v2'}
 assert e,f'unable to prove Firebase Functions generation for exported module: {rel}'
 g|=e
 if 'v1' in e: v1files.append(rel)
 if 'v2' in e: v2files.append(rel)
assert g=={'v1','v2'},f'expected mixed v1/v2 deployed generations, got {sorted(g)}'
assert v1files==['functions/src/pushAccountCleanup.js'],f'unexpected deployed v1 modules: {v1files}'
assert v2files,'v2 runtime not independently proven'
cleanup=(r/'functions/src/pushAccountCleanup.js').read_text(errors='replace')
assert 'require("firebase-functions/v1")' in cleanup or "require('firebase-functions/v1')" in cleanup
assert 'firebase-functions/params' in cleanup and 'projectID' in cleanup
assert prod in cleanup and v1sa in cleanup
assert re.search(r'projectID\s*\.\s*equals\s*\(\s*["\']'+re.escape(prod)+r'["\']\s*\)',cleanup)
assert re.search(r'thenElse\s*\(\s*PRODUCTION_AUTH_CLEANUP_SERVICE_ACCOUNT\s*,\s*["\']default["\']\s*\)',cleanup)
assert re.search(r'runWith\s*\(\s*\{\s*serviceAccount\s*:\s*authCleanupServiceAccount\s*\}\s*\)',cleanup)
assert re.search(r'exports\.onPushAccountDeleted\s*=.*?\.auth\.user\(\)\.onDelete',cleanup,re.S)
index=(r/'functions/src/index.js').read_text(errors='replace')
assert 'firebase-functions/params' in index and 'projectID' in index
assert v2sa in index and prod in index
assert re.search(r'projectID\s*\.\s*equals\s*\(\s*["\']'+re.escape(prod)+r'["\']\s*\)',index)
assert re.search(r'thenElse\s*\(\s*PRODUCTION_V2_SERVICE_ACCOUNT\s*,\s*["\']default["\']\s*\)',index)
assert re.search(r'setGlobalOptions\s*\(\s*\{.*?serviceAccount\s*:\s*productionV2ServiceAccount.*?\}\s*\)',index,re.S)
for rel in deployed:
 x=(r/rel).read_text(errors='replace')
 if rel!='functions/src/pushAccountCleanup.js': assert v1sa not in x,f'Production v1 runtime SA leaked into another deployed module: {rel}'
 if rel!='functions/src/index.js': assert v2sa not in x,f'Production v2 runtime SA leaked outside common v2 configuration: {rel}'
wf=(r/'.github/workflows/production-release.yml').read_text(); z=re.sub(r'\\\n\s*',' ',wf); z=re.sub(r'\s+',' ',z)
assert '--only firestore:rules,firestore:indexes,functions' in z and '--service-account' not in wf and '--build-service-account' not in wf
print(','.join(sorted(g)))
print('|'.join(v1files))
print('|'.join(v2files))
PY
)" || fatal 'repository runtime/build identity configuration is not the proven Block 3B.3C shape'
GENS="${A%%$'\n'*}"
REST="${A#*$'\n'}"
V1_FILES="${REST%%$'\n'*}"
V2_FILES="${REST#*$'\n'}"
printf 'INFO  deployed Firebase Functions generations: %s\n' "$GENS"
printf 'INFO  v1 deployed modules: %s\n' "$V1_FILES"
printf 'INFO  v2 deployed modules: %s\n' "$V2_FILES"
pass 'repository export surface, dedicated v1 identity, and independent v2 generation proven'

RUNTIME_SAS=()
add_unique "$V1_RUNTIME_SA" RUNTIME_SAS
add_unique "$V2_RUNTIME_SA" RUNTIME_SAS

valid_sa "$V1_RUNTIME_SA"
V1_EMAIL="$(gcloud iam service-accounts describe "$V1_RUNTIME_SA" --project="$P" --format='value(email)' 2>/dev/null || true)"
if [[ -z "$V1_EMAIL" ]]; then
  [[ "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]] || fatal "dedicated v1 runtime identity absent: $V1_RUNTIME_SA"
  pass 'pre-bootstrap mode permits only the exact dedicated v1 runtime SA to be absent'
else
  [[ "$V1_EMAIL" == "$V1_RUNTIME_SA" ]] || fatal "dedicated v1 runtime identity mismatch: $V1_EMAIL"
  V1_KEYS="$(gcloud iam service-accounts keys list --iam-account="$V1_RUNTIME_SA" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^$/d' | wc -l | tr -d '[:space:]')"
  [[ "$V1_KEYS" == 0 ]] || fatal "dedicated v1 runtime SA has user-managed keys: $V1_KEYS"
  mapfile -t V1_ROLES < <(roles_for "$V1_RUNTIME_SA")
  for q in "${V1_ROLES[@]}"; do [[ "$q" == "$V1_RUNTIME_ROLE" ]] || fatal "dedicated v1 runtime SA holds unexpected project role: $q"; done
  if [[ "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 0 ]]; then
    [[ ${#V1_ROLES[@]} -eq 1 && "${V1_ROLES[0]}" == "$V1_RUNTIME_ROLE" ]] || fatal "dedicated v1 runtime SA project roles must equal $V1_RUNTIME_ROLE"
  fi
  [[ -n "$(gcloud iam service-accounts get-iam-policy "$V1_RUNTIME_SA" --project="$P" --format=json 2>/dev/null || true)" ]] || fatal "unable to inspect dedicated v1 runtime SA IAM policy"
  pass 'dedicated v1 runtime identity exact; zero user-managed keys; runtime roles bounded'
fi

valid_sa "$V2_RUNTIME_SA"
V2_EMAIL="$(gcloud iam service-accounts describe "$V2_RUNTIME_SA" --project="$P" --format='value(email)' 2>/dev/null || true)"
if [[ -z "$V2_EMAIL" ]]; then
  [[ "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]] || fatal "dedicated v2 runtime identity absent: $V2_RUNTIME_SA"
  pass 'pre-bootstrap mode permits only the exact dedicated v2 runtime SA to be absent'
else
  [[ "$V2_EMAIL" == "$V2_RUNTIME_SA" ]] || fatal "dedicated v2 runtime identity mismatch: $V2_EMAIL"
  V2_KEYS="$(gcloud iam service-accounts keys list --iam-account="$V2_RUNTIME_SA" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^$/d' | wc -l | tr -d '[:space:]')"
  [[ "$V2_KEYS" == 0 ]] || fatal "dedicated v2 runtime SA has user-managed keys: $V2_KEYS"
  mapfile -t V2_ROLES < <(roles_for "$V2_RUNTIME_SA")
  [[ ${#V2_ROLES[@]} -eq 0 ]] || fatal "dedicated v2 runtime SA must have zero project roles: ${V2_ROLES[*]}"
  [[ -n "$(gcloud iam service-accounts get-iam-policy "$V2_RUNTIME_SA" --project="$P" --format=json 2>/dev/null || true)" ]] || fatal "unable to inspect dedicated v2 runtime SA IAM policy"
  pass 'dedicated v2 runtime identity exact; zero user-managed keys; zero project roles'
fi

SE="$(mktemp)"
BE="$(mktemp)"
trap 'rm -f "$SE" "$BE"' EXIT
CLOUD_BUILD_SERVICE_ENABLED=false
BUILD_IDENTITY_DISCOVERY_ATTEMPTED=false
BUILD_SA=""
PRODUCTION_BUILD_IDENTITY_STATUS="$BUILD_DEFERRED_STATUS"

set +e
SERVICE_ROWS="$(gcloud services list --project="$P" --enabled --filter="config.name:$CLOUD_BUILD_SERVICE" --format='value(config.name)' 2>"$SE")"
SS=$?
set -e
SERVICE_STATE_ERROR="$(tr '\n' ' ' <"$SE")"
[[ $SS -eq 0 ]] || fatal "Cloud Build service-state query failed; no API was enabled. gcloud: $SERVICE_STATE_ERROR"
mapfile -t ENABLED_BUILD_SERVICES < <(printf '%s\n' "$SERVICE_ROWS" | sed '/^$/d' | sort -u)

if [[ ${#ENABLED_BUILD_SERVICES[@]} -eq 0 ]]; then
  pass 'Cloud Build service is not enabled; build identity deferred without API enablement'
elif [[ ${#ENABLED_BUILD_SERVICES[@]} -eq 1 && "${ENABLED_BUILD_SERVICES[0]}" == "$CLOUD_BUILD_SERVICE" ]]; then
  CLOUD_BUILD_SERVICE_ENABLED=true
  BUILD_IDENTITY_DISCOVERY_ATTEMPTED=true
  set +e
  BR="$(gcloud builds get-default-service-account --project="$P" --region="$REGION" --format='value(serviceAccountEmail)' 2>"$BE")"
  BS=$?
  set -e
  BUILD_DISCOVERY_ERROR="$(tr '\n' ' ' <"$BE")"
  [[ $BS -eq 0 ]] || fatal "Cloud Build default service-account discovery failed after enabled-service verification; no API was enabled. gcloud: $BUILD_DISCOVERY_ERROR"
  BUILD_SA="${BR#projects/$P/serviceAccounts/}"
  BUILD_SA="${BUILD_SA//$'\r'/}"
  BUILD_SA="${BUILD_SA//$'\n'/}"
  if [[ -z "$BUILD_SA" ]]; then
    pass 'Cloud Build service enabled but discovery returned no default identity; build identity deferred without API enablement'
  else
    PRODUCTION_BUILD_IDENTITY_STATUS="READY"
  fi
else
  fatal "Cloud Build enabled-service inventory is ambiguous: ${ENABLED_BUILD_SERVICES[*]}"
fi

if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" ]]; then
  valid_sa "$BUILD_SA"
  [[ "$(gcloud iam service-accounts describe "$BUILD_SA" --project="$P" --format='value(email)' 2>/dev/null || true)" == "$BUILD_SA" ]] || fatal "build identity mismatch or absent: $BUILD_SA"
  mapfile -t BUILD_ROLES < <(roles_for "$BUILD_SA")
  for q in "${BUILD_ROLES[@]}"; do
    if contains "$q" "${BAD_ROLES[@]}" || [[ "$q" =~ ^roles/.+\.serviceAgent$ ]]; then
      fatal "discovered build identity $BUILD_SA holds forbidden role: $q"
    fi
  done
  [[ -n "$(gcloud iam service-accounts get-iam-policy "$BUILD_SA" --project="$P" --format=json 2>/dev/null || true)" ]] || fatal "unable to inspect build SA IAM policy: $BUILD_SA"
  pass "Cloud Build identity live-discovered: $BUILD_SA"
fi

INTENDED=()
for s in "${RUNTIME_SAS[@]}"; do add_unique "$s" INTENDED; done
if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" ]]; then
  add_unique "$BUILD_SA" INTENDED
fi

mapfile -t ALL < <(gcloud iam service-accounts list --project="$P" --format='value(email)' 2>/dev/null | sed '/^$/d' | sort -u)
for s in "${INTENDED[@]}"; do
  if [[ "$s" == "$V1_RUNTIME_SA" && -z "$V1_EMAIL" && "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]]; then continue; fi
  if [[ "$s" == "$V2_RUNTIME_SA" && -z "$V2_EMAIL" && "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]]; then continue; fi
  contains "$s" "${ALL[@]}" || fatal "required identity absent from Production inventory: $s"
done
for s in "${ALL[@]}"; do
  if has_actas "$s"; then contains "$s" "${INTENDED[@]}" || fatal "accidental actAs on unintended Production SA: $s"; fi
done
if [[ "$ALLOW_MISSING_ACTAS" == 0 ]]; then
  for s in "${RUNTIME_SAS[@]}"; do has_actas "$s" || fatal "missing deploy-SA roles/iam.serviceAccountUser on intended runtime identity: $s"; done
  if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" ]]; then
    has_actas "$BUILD_SA" || fatal "missing deploy-SA roles/iam.serviceAccountUser on intended build identity: $BUILD_SA"
  fi
  pass 'actAs exact on intended runtime identities and build identity when ready'
else
  pass 'pre-mutation mode allows intended actAs bindings absent'
fi

URL="https://api.github.com/repos/$REPO/deployments?environment=production&per_page=1"
H=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
[[ -n "${GH_TOKEN:-}" ]] && H+=(-H "Authorization: Bearer $GH_TOKEN")
[[ -z "${GH_TOKEN:-}" && -n "${GITHUB_TOKEN:-}" ]] && H+=(-H "Authorization: Bearer $GITHUB_TOKEN")
D="$(curl -fsSL "${H[@]}" "$URL" 2>/dev/null || true)"
[[ -n "$D" ]] || fatal 'unable to verify Production deployments = 0'
printf '%s' "$D" | python3 -c 'import json,sys;x=json.load(sys.stdin);assert isinstance(x,list) and not x' || fatal 'Production deployment inventory is not empty'
pass 'Production deployments = 0'

if [[ -n "$DISCOVERY_OUTPUT" ]]; then
  umask 077
  {
    printf 'RUNTIME_SAS=('; for s in "${RUNTIME_SAS[@]}"; do printf ' %q' "$s"; done; printf ' )\n'
    printf 'BUILD_SA=%q\n' "$BUILD_SA"
    printf 'PRODUCTION_BUILD_IDENTITY_STATUS=%q\n' "$PRODUCTION_BUILD_IDENTITY_STATUS"
    printf 'CLOUD_BUILD_SERVICE_ENABLED=%q\n' "$CLOUD_BUILD_SERVICE_ENABLED"
    printf 'BUILD_IDENTITY_DISCOVERY_ATTEMPTED=%q\n' "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED"
  } >"$DISCOVERY_OUTPUT"
fi

RUNTIME_STATUS="READY"
if [[ "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 && ( -z "$V1_EMAIL" || -z "$V2_EMAIL" ) ]]; then
  RUNTIME_STATUS="BOOTSTRAP_REQUIRED"
fi
printf '\nProduction runtime/build actAs verification PASSED.\n'
printf 'runtimeServiceAccounts=%s\n' "$(IFS=,; printf '%s' "${RUNTIME_SAS[*]}")"
printf 'productionRuntimeIdentityStatus=%s\n' "$RUNTIME_STATUS"
printf 'buildServiceAccount=%s\n' "$BUILD_SA"
printf 'productionBuildIdentityStatus=%s\n' "$PRODUCTION_BUILD_IDENTITY_STATUS"
printf 'productionCloudBuildServiceEnabled=%s\n' "$CLOUD_BUILD_SERVICE_ENABLED"
printf 'productionBuildIdentityDiscoveryAttempted=%s\n' "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED"
printf 'productionDeployIamConfigured=true\n'
if [[ "$ALLOW_MISSING_ACTAS" == 1 || "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]]; then
  printf 'productionRuntimeActAsConfigured=false\n'
else
  printf 'productionRuntimeActAsConfigured=true\n'
fi
if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" && "$ALLOW_MISSING_ACTAS" == 0 && "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 0 ]]; then
  printf 'productionRuntimeBuildActAsConfigured=true\n'
else
  printf 'productionRuntimeBuildActAsConfigured=false\n'
fi
printf 'productionWifConfigured=true\nproductionWifEndToEndVerified=false\nproductionDeployEndToEndReady=false\nproductionIdentityReady=false\nproductionDeployed=false\n