#!/usr/bin/env bash
set -euo pipefail

readonly PROJECT_ID="clickandsaveai-staging"
readonly LOCATION="us-central1"
readonly POLICY_NAME="firebase-functions-cleanup"
readonly EVIDENCE_PATH="${STAGING_ARTIFACT_CLEANUP_EVIDENCE:-${RUNNER_TEMP:-/tmp}/staging-artifact-cleanup-preflight.json}"

for tool in gcloud jq curl; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required tool is unavailable: $tool" >&2
    exit 1
  fi
done

mkdir -p "$(dirname "$EVIDENCE_PATH")"

repos_json="$(gcloud artifacts repositories list \
  --project="$PROJECT_ID" \
  --location="$LOCATION" \
  --format=json)"

deployment_repos="$(printf '%s' "$repos_json" | jq -r '
  .[]
  | select((.format // "" | ascii_upcase) == "DOCKER")
  | .name as $full
  | ($full | split("/") | last) as $id
  | select($id == "gcf-artifacts")
  | $id
')"

if [[ -z "$deployment_repos" ]]; then
  echo "No Cloud Functions deployment Artifact Registry repository (gcf-artifacts) was discovered in $PROJECT_ID/$LOCATION." >&2
  exit 1
fi

repo_count="$(printf '%s\n' "$deployment_repos" | sed '/^$/d' | wc -l | tr -d ' ')"
results='[]'

policy_is_equivalent() {
  local policies_json="$1"
  local dry_run="$2"
  [[ "${dry_run,,}" != "true" ]] || return 1
  printf '%s' "$policies_json" | jq -e --arg name "$POLICY_NAME" '
    any(.[];
      .name == $name
      and ((.action.type // "" | ascii_downcase) == "delete")
      and ((.condition.tagState // "" | ascii_downcase) == "any")
      and ((.condition.olderThan // "") | IN("1d", "24h", "86400s", "86400.0s"))
    )
  ' >/dev/null
}

while IFS= read -r repo; do
  [[ -n "$repo" ]] || continue

  describe_before="$(gcloud artifacts repositories describe "$repo" \
    --project="$PROJECT_ID" \
    --location="$LOCATION" \
    --format=json)"
  policies_before="$(gcloud artifacts repositories list-cleanup-policies "$repo" \
    --project="$PROJECT_ID" \
    --location="$LOCATION" \
    --format=json)"

  format="$(printf '%s' "$describe_before" | jq -r '.format // "UNKNOWN"')"
  dry_run_before="$(printf '%s' "$describe_before" | jq -r '.cleanupPolicyDryRun // false')"

  access_token="$(gcloud auth print-access-token)"
  permission_response="$(curl -fsS \
    -X POST \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    "https://artifactregistry.googleapis.com/v1/projects/${PROJECT_ID}/locations/${LOCATION}/repositories/${repo}:testIamPermissions" \
    --data-binary '{"permissions":["artifactregistry.repositories.get","artifactregistry.repositories.update","artifactregistry.versions.delete"]}')"
  unset access_token

  can_get="$(printf '%s' "$permission_response" | jq -r '(.permissions // []) | index("artifactregistry.repositories.get") != null')"
  can_update="$(printf '%s' "$permission_response" | jq -r '(.permissions // []) | index("artifactregistry.repositories.update") != null')"
  can_versions_delete="$(printf '%s' "$permission_response" | jq -r '(.permissions // []) | index("artifactregistry.versions.delete") != null')"

  if [[ "$can_get" != "true" ]]; then
    echo "Authenticated Staging deploy identity lacks artifactregistry.repositories.get on $repo." >&2
    exit 1
  fi
  if [[ "$can_update" != "true" ]]; then
    echo "Authenticated Staging deploy identity lacks required artifactregistry.repositories.update on $repo. No IAM mutation was attempted." >&2
    exit 1
  fi

  changed=false
  if ! policy_is_equivalent "$policies_before" "$dry_run_before"; then
    policy_file="$(mktemp)"
    trap 'rm -f "${policy_file:-}"' EXIT

    # Preserve every unrelated cleanup policy. Normalize only enum spelling from
    # gcloud's read representation before adding the deterministic Firebase policy.
    printf '%s' "$policies_before" | jq --arg name "$POLICY_NAME" '
      [
        .[]
        | select(.name != $name)
        | if .action.type? then
            .action.type = (if (.action.type | ascii_downcase) == "delete" then "Delete" else "Keep" end)
          else . end
        | if .condition.tagState? then
            .condition.tagState = (.condition.tagState | ascii_downcase)
          else . end
      ]
      + [{
          name: $name,
          action: {type: "Delete"},
          condition: {tagState: "any", olderThan: "1d"}
        }]
    ' > "$policy_file"

    gcloud artifacts repositories set-cleanup-policies "$repo" \
      --project="$PROJECT_ID" \
      --location="$LOCATION" \
      --policy="$policy_file" \
      --no-dry-run \
      --quiet
    rm -f "$policy_file"
    trap - EXIT
    changed=true
  fi

  describe_after="$(gcloud artifacts repositories describe "$repo" \
    --project="$PROJECT_ID" \
    --location="$LOCATION" \
    --format=json)"
  policies_after="$(gcloud artifacts repositories list-cleanup-policies "$repo" \
    --project="$PROJECT_ID" \
    --location="$LOCATION" \
    --format=json)"
  dry_run_after="$(printf '%s' "$describe_after" | jq -r '.cleanupPolicyDryRun // false')"

  if ! policy_is_equivalent "$policies_after" "$dry_run_after"; then
    echo "Canonical active cleanup policy verification failed for $repo." >&2
    exit 1
  fi

  result="$(jq -n \
    --arg repository "$repo" \
    --arg format "$format" \
    --argjson cleanupPolicyDryRunBefore "$dry_run_before" \
    --argjson cleanupPolicyDryRunAfter "$dry_run_after" \
    --argjson changed "$changed" \
    --argjson canList true \
    --argjson canGet "$can_get" \
    --argjson canUpdate "$can_update" \
    --argjson canVersionsDelete "$can_versions_delete" \
    --argjson policiesBefore "$policies_before" \
    --argjson policiesAfter "$policies_after" \
    '{
      repository: $repository,
      format: $format,
      cleanupPolicyDryRunBefore: $cleanupPolicyDryRunBefore,
      cleanupPolicyDryRunAfter: $cleanupPolicyDryRunAfter,
      changed: $changed,
      deployIdentityPermissions: {
        "artifactregistry.repositories.list": $canList,
        "artifactregistry.repositories.get": $canGet,
        "artifactregistry.repositories.update": $canUpdate,
        "artifactregistry.versions.delete": $canVersionsDelete
      },
      policiesBefore: $policiesBefore,
      policiesAfter: $policiesAfter
    }')"
  results="$(jq -n --argjson current "$results" --argjson item "$result" '$current + [$item]')"

done <<< "$deployment_repos"

all_repositories="$(printf '%s' "$repos_json" | jq '[.[] | {repository: (.name | split("/") | last), format: (.format // "UNKNOWN")} ]')"

jq -n \
  --arg project "$PROJECT_ID" \
  --arg location "$LOCATION" \
  --arg canonicalPolicy "$POLICY_NAME" \
  --argjson deploymentRepositoryCount "$repo_count" \
  --argjson allRepositories "$all_repositories" \
  --argjson deploymentRepositories "$results" \
  '{
    project: $project,
    location: $location,
    canonicalPolicy: $canonicalPolicy,
    deploymentRepositoryCount: $deploymentRepositoryCount,
    allRepositories: $allRepositories,
    deploymentRepositories: $deploymentRepositories
  }' > "$EVIDENCE_PATH"

jq -c --arg canonical "$POLICY_NAME" '{
  project,
  location,
  allRepositories,
  deploymentRepositoryCount,
  deploymentRepositories: [
    .deploymentRepositories[]
    | {
        repository,
        format,
        cleanupPolicyDryRunBefore,
        cleanupPolicyDryRunAfter,
        changed,
        deployIdentityPermissions,
        policiesBefore,
        policiesAfter,
        canonicalBefore: ([.policiesBefore[] | select(.name == $canonical)][0] // null),
        canonicalAfter: ([.policiesAfter[] | select(.name == $canonical)][0] // null)
      }
  ]
}' "$EVIDENCE_PATH"
echo "Artifact Registry cleanup preflight verified canonical active policy in $PROJECT_ID/$LOCATION."
