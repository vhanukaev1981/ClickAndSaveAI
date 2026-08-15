#!/usr/bin/env bash
set -euo pipefail

P=click-save-ai-production
N=991489557172
DEPLOY="clickandsaveai-github-deployer@$P.iam.gserviceaccount.com"
V1_RUNTIME_SA="clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com"
V2_RUNTIME_SA="991489557172-compute@developer.gserviceaccount.com"
V1_RUNTIME_ROLE="roles/datastore.user"
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
BAD_ROLES=(roles/owner roles/editor roles/firebase.admin roles/firebase.developAdmin roles/cloudfunctions.admin roles/run.admin roles/resourcemanager.projectIamAdmin roles/iam.serviceAccountAdmin roles/iam.serviceAccountTokenCreator roles/iam.serviceAccountUser roles/secretmanager.admin roles/storage.admin roles/artifactregistry.admin)

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
    "$V1_RUNTIME_SA"|"$V2_RUNTIME_SA"|"$N@cloudbuild.gserviceaccount.com"|*"@$P.iam.gserviceaccount.com") : ;;
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

A="$(python3 - "$ROOT" "$P" "$V1_RUNTIME_SA" <<'PY'
from pathlib import Path
import json,re,sys
r=Path(sys.argv[1]).resolve(); prod=sys.argv[2]; v1sa=sys.argv[3]
f=json.loads((r/'firebase.json').read_text()); fn=f.get('functions',{})
assert isinstance(fn,dict) and fn.get('source')=='functions' and fn.get('codebase')=='default' and fn.get('runtime')=='nodejs22'
def bad(v):
 if isinstance(v,dict): return any(re.sub('[^a-z]','',str(k).lower()) in {'serviceaccount','serviceaccountemail','buildserviceaccount'} or bad(x) for k,x in v.items())
 if isinstance(v,list): return any(map(bad,v))
 return False
assert not bad(f),'global Firebase service-account configuration present'
pkg=json.loads((r/'functions/package.json').read_text()); main=pkg.get('main'); assert isinstance(main,str) and main
entry=(r/'functions'/main).resolve(); src=(r/'functions/src').resolve(); assert entry.is_file() and (entry.parent==src or src in entry.parents)
IR=re.compile(r'''(?:require\s*\(\s*|\bfrom\s*)["'](firebase-functions(?:/[^"']+)?)['"]\s*\)?''')
def cl(s):
 if s=='firebase-functions/v1' or s.startswith('firebase-functions/v1/'): return 'v1'
 if s=='firebase-functions/v2' or s.startswith('firebase-functions/v2/'): return 'v2'
 return 'neutral'
t=entry.read_text(errors='replace'); binds={}
for n,q in re.findall(r'''\bconst\s+([\w$]+)\s*=\s*require\s*\(\s*["'](\./[^"']+)["']\s*\)''',t):
 p=(entry.parent/q); p=p if p.suffix else p.with_suffix('.js'); binds[n]=p.resolve()
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
for rel in deployed:
 if rel!='functions/src/pushAccountCleanup.js':
  assert v1sa not in (r/rel).read_text(errors='replace'),f'Production v1 runtime SA leaked into another deployed module: {rel}'
wf=(r/'.github/workflows/production-release.yml').read_text(); z=re.sub(r'\\\n\s*',' ',wf); z=re.sub(r'\s+',' ',z)
assert '--only firestore:rules,firestore:indexes,functions' in z and '--service-account' not in wf and '--build-service-account' not in wf
print(','.join(sorted(g)))
print('|'.join(v1files))
print('|'.join(v2files))
PY
)" || fatal 'repository runtime/build identity configuration is not the proven Block 3B.3B shape'
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
[[ "$(gcloud iam service-accounts describe "$V2_RUNTIME_SA" --project="$P" --format='value(email)' 2>/dev/null || true)" == "$V2_RUNTIME_SA" ]] || fatal "v2 runtime identity mismatch or absent: $V2_RUNTIME_SA"
pass "required v2 runtime identity exists: $V2_RUNTIME_SA"

BE="$(mktemp)"
trap 'rm -f "$BE"' EXIT
set +e
BR="$(gcloud builds get-default-service-account --project="$P" --region="$REGION" --format='value(serviceAccountEmail)' 2>"$BE")"
BS=$?
set -e
[[ $BS -eq 0 ]] || fatal "Cloud Build default service-account discovery failed; no API was enabled. gcloud: $(tr '\n' ' ' <"$BE")"
BUILD_SA="${BR#projects/$P/serviceAccounts/}"
BUILD_SA="${BUILD_SA//$'\r'/}"
BUILD_SA="${BUILD_SA//$'\n'/}"
[[ -n "$BUILD_SA" ]] || fatal 'Cloud Build discovery returned an empty identity; no API was enabled or substituted'
valid_sa "$BUILD_SA"
[[ "$(gcloud iam service-accounts describe "$BUILD_SA" --project="$P" --format='value(email)' 2>/dev/null || true)" == "$BUILD_SA" ]] || fatal "build identity mismatch or absent: $BUILD_SA"
pass "Cloud Build identity live-discovered: $BUILD_SA"

INTENDED=()
for s in "${RUNTIME_SAS[@]}"; do add_unique "$s" INTENDED; done
add_unique "$BUILD_SA" INTENDED
for s in "${INTENDED[@]}"; do
  [[ "$s" == "$V1_RUNTIME_SA" ]] && continue
  mapfile -t RR < <(roles_for "$s")
  for q in "${RR[@]}"; do
    if contains "$q" "${BAD_ROLES[@]}" || [[ "$q" =~ ^roles/.+\.serviceAgent$ ]]; then
      fatal "discovered identity $s holds forbidden role: $q"
    fi
  done
  [[ -n "$(gcloud iam service-accounts get-iam-policy "$s" --project="$P" --format=json 2>/dev/null || true)" ]] || fatal "unable to inspect SA IAM policy: $s"
done
pass 'v2/build identity risk audit passed'

mapfile -t ALL < <(gcloud iam service-accounts list --project="$P" --format='value(email)' 2>/dev/null | sed '/^$/d' | sort -u)
for s in "${INTENDED[@]}"; do
  if [[ "$s" == "$V1_RUNTIME_SA" && -z "$V1_EMAIL" && "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]]; then continue; fi
  contains "$s" "${ALL[@]}" || fatal "required identity absent from Production inventory: $s"
done
for s in "${ALL[@]}"; do
  if has_actas "$s"; then contains "$s" "${INTENDED[@]}" || fatal "accidental actAs on unintended Production SA: $s"; fi
done
if [[ "$ALLOW_MISSING_ACTAS" == 0 ]]; then
  for s in "${INTENDED[@]}"; do has_actas "$s" || fatal "missing deploy-SA roles/iam.serviceAccountUser on intended identity: $s"; done
  pass 'actAs exact on intended runtime/build identities'
else
  pass 'pre-mutation mode allows intended actAs bindings absent'
fi

URL="https://api.github.com/repos/$REPO/deployments?environment=production&per_page=1"
H=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
[[ -n "${GH_TOKEN:-}" ]] && H+=(-H "Authorization: Bearer $GH_TOKEN")
[[ -z "${GH_TOKEN:-}" && -n "${GITHUB_TOKEN:-}" ]] && H+=(-H "Authorization: Bearer $GITHUB_TOKEN")
D="$(curl -fsSL "${H[@]}" "$URL" 2>/dev/null || true)"
[[ -n "$D" ]] && printf '%s' "$D" | python3 -c 'import json,sys;x=json.load(sys.stdin);assert isinstance(x,list) and not x' || fatal 'unable to verify Production deployments = 0'
pass 'Production deployments = 0'

if [[ -n "$DISCOVERY_OUTPUT" ]]; then
  umask 077
  {
    printf 'RUNTIME_SAS=('; for s in "${RUNTIME_SAS[@]}"; do printf ' %q' "$s"; done; printf ' )\n'
    printf 'BUILD_SA=%q\n' "$BUILD_SA"
  } >"$DISCOVERY_OUTPUT"
fi
printf '\nProduction runtime/build actAs verification PASSED.\n'
printf 'runtimeServiceAccounts=%s\n' "$(IFS=,; printf '%s' "${RUNTIME_SAS[*]}")"
printf 'buildServiceAccount=%s\n' "$BUILD_SA"
printf 'productionDeployIamConfigured=true\n'
[[ "$ALLOW_MISSING_ACTAS" == 1 || "$ALLOW_RUNTIME_BOOTSTRAP_GAP" == 1 ]] && printf 'productionRuntimeBuildActAsConfigured=false\n' || printf 'productionRuntimeBuildActAsConfigured=true\n'
printf 'productionWifConfigured=true\nproductionWifEndToEndVerified=false\nproductionDeployEndToEndReady=false\nproductionIdentityReady=false\nproductionDeployed=false\n'
