#!/bin/bash
# Seed script: creates ~500 roles, ~150 agent roles, 2500 user assignments, 250 groups
# Usage: ./seed-data.sh [JENKINS_URL]
set -euo pipefail

JENKINS="${1:-http://localhost:8080/jenkins}"
API="$JENKINS/role-strategy/strategy"
JENKINS_USER="${JENKINS_USERNAME}"
JENKINS_TOKEN="${JENKINS_API_TOKEN}"

echo "=== Seeding role-strategy data at $JENKINS ==="
echo "Authenticating as $JENKINS_USER..."
curl -sf -u "$JENKINS_USER:$JENKINS_TOKEN" "$JENKINS/api/json" > /dev/null || { echo "ERROR: Cannot reach Jenkins"; exit 1; }
echo "  Connected OK"

api_post() {
  local url="$1"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "$JENKINS_USER:$JENKINS_TOKEN" "$url") || true
  [ "$http_code" = "200" ]
}

OK=0; FAIL=0

# --- Global Roles (100) ---
echo ""
echo "Creating 100 global roles..."

GLOBAL_PERMS=(
  "hudson.model.Hudson.Administer"
  "hudson.model.Hudson.Read"
  "hudson.model.Hudson.Manage,hudson.model.Hudson.Read"
  "hudson.model.Hudson.Read"
  "hudson.model.Hudson.Read,hudson.model.Hudson.Manage"
)
GLOBAL_PREFIXES=("super" "senior" "junior" "lead" "staff" "principal" "associate" "chief" "deputy" "acting" "interim" "temp" "contract" "remote" "onsite" "global" "regional" "local" "external" "internal")
GLOBAL_SUFFIXES=("admin" "reader" "operator" "monitor" "engineer" "analyst" "architect" "manager" "coordinator" "specialist" "consultant" "advisor" "officer" "director" "supervisor" "technician" "developer" "tester" "auditor" "reviewer")

for i in $(seq 0 99); do
  pi=$((i / 20))
  si=$((i % 20))
  name="${GLOBAL_PREFIXES[$pi]}-${GLOBAL_SUFFIXES[$si]}"
  perms="${GLOBAL_PERMS[$((i % ${#GLOBAL_PERMS[@]}))]}"
  if api_post "$API/addRole?type=globalRoles&roleName=$name&permissionIds=$perms&overwrite=false"; then
    ((OK++))
  else
    ((FAIL++))
  fi
done
echo "  Global roles: $OK ok, $FAIL fail"

# --- Item Roles (250) ---
echo ""
echo "Creating 250 item roles..."
S0=$OK; F0=$FAIL

ITEM_PERMS=(
  "hudson.model.Item.Read,hudson.model.Item.Build"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure,hudson.model.Item.Workspace"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure,hudson.model.Item.Create"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure,hudson.model.Item.Create,hudson.model.Item.Delete"
  "hudson.model.Item.Read"
  "hudson.model.Item.Read,hudson.model.Item.Discover"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Cancel"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure,hudson.model.Item.Move"
  "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Workspace,hudson.model.Run.Update"
)
ITEM_PREFIXES=("frontend" "backend" "mobile" "data" "infra" "devops" "platform" "cloud" "security" "analytics" "ml" "api" "web" "desktop" "embedded" "iot" "blockchain" "ai" "microservice" "monolith" "gateway" "proxy" "cache" "queue" "stream" "batch" "realtime" "legacy" "modern" "hybrid" "serverless" "container" "native" "cross-platform" "enterprise" "startup" "fintech" "healthtech" "edtech" "gamedev" "media" "social" "commerce" "payments" "identity" "messaging" "storage" "compute" "network" "database")
ITEM_SUFFIXES=("dev" "staging" "prod" "test" "qa" "uat" "demo" "sandbox" "preview" "canary" "blue" "green" "alpha" "beta" "rc" "stable" "nightly" "weekly" "release" "hotfix" "feature" "bugfix" "spike" "poc" "mvp" "v1" "v2" "v3" "next" "current" "archive" "backup" "mirror" "primary" "secondary" "failover" "dr" "perf" "load" "stress" "smoke" "regression" "integration" "e2e" "unit" "contract" "security-scan" "lint" "build" "deploy")

for i in $(seq 0 249); do
  pi=$((i % ${#ITEM_PREFIXES[@]}))
  si=$((i / ${#ITEM_PREFIXES[@]} % ${#ITEM_SUFFIXES[@]}))
  name="${ITEM_PREFIXES[$pi]}-${ITEM_SUFFIXES[$si]}"
  pattern="${ITEM_PREFIXES[$pi]}-${ITEM_SUFFIXES[$si]}-.*"
  perms="${ITEM_PERMS[$((i % ${#ITEM_PERMS[@]}))]}"
  if api_post "$API/addRole?type=projectRoles&roleName=$name&permissionIds=$perms&overwrite=false&pattern=$pattern"; then
    ((OK++))
  else
    ((FAIL++))
  fi
done
echo "  Item roles: $((OK-S0)) ok, $((FAIL-F0)) fail"

# --- Agent Roles (150) ---
echo ""
echo "Creating 150 agent roles..."
S0=$OK; F0=$FAIL

AGENT_PREFIXES=("linux" "windows" "macos" "docker" "k8s" "gpu" "arm" "x86" "spot" "onprem" "cloud" "aws" "gcp" "azure" "metal" "vm" "lxc" "podman" "nerdctl" "containerd" "crio" "fargate" "ecs" "eks" "aks" "gke" "openshift" "rancher" "nomad" "mesos")
AGENT_SUFFIXES=("builder" "runner" "executor" "worker" "node" "agent" "slave" "host" "instance" "pod" "container" "machine" "server" "cluster" "pool" "fleet" "farm" "grid" "swarm" "herd" "pack" "squad" "brigade" "team" "unit" "cell" "zone" "region" "rack" "bay")

for i in $(seq 0 149); do
  pi=$((i % ${#AGENT_PREFIXES[@]}))
  si=$((i / ${#AGENT_PREFIXES[@]} % ${#AGENT_SUFFIXES[@]}))
  name="${AGENT_PREFIXES[$pi]}-${AGENT_SUFFIXES[$si]}"
  pattern="${AGENT_PREFIXES[$pi]}-${AGENT_SUFFIXES[$si]}-.*"
  if api_post "$API/addRole?type=slaveRoles&roleName=$name&permissionIds=hudson.model.Computer.Build,hudson.model.Computer.Connect&overwrite=false&pattern=$pattern"; then
    ((OK++))
  else
    ((FAIL++))
  fi
done
echo "  Agent roles: $((OK-S0)) ok, $((FAIL-F0)) fail"

# --- Collect role names for assignment ---
GLOBAL_ROLE_NAMES=()
for i in $(seq 0 99); do
  pi=$((i / 20)); si=$((i % 20))
  GLOBAL_ROLE_NAMES+=("${GLOBAL_PREFIXES[$pi]}-${GLOBAL_SUFFIXES[$si]}")
done
ITEM_ROLE_NAMES=()
for i in $(seq 0 249); do
  pi=$((i % ${#ITEM_PREFIXES[@]})); si=$((i / ${#ITEM_PREFIXES[@]} % ${#ITEM_SUFFIXES[@]}))
  ITEM_ROLE_NAMES+=("${ITEM_PREFIXES[$pi]}-${ITEM_SUFFIXES[$si]}")
done
AGENT_ROLE_NAMES=()
for i in $(seq 0 149); do
  pi=$((i % ${#AGENT_PREFIXES[@]})); si=$((i / ${#AGENT_PREFIXES[@]} % ${#AGENT_SUFFIXES[@]}))
  AGENT_ROLE_NAMES+=("${AGENT_PREFIXES[$pi]}-${AGENT_SUFFIXES[$si]}")
done

NUM_GLOBAL=${#GLOBAL_ROLE_NAMES[@]}
NUM_ITEM=${#ITEM_ROLE_NAMES[@]}
NUM_AGENT=${#AGENT_ROLE_NAMES[@]}

# --- User Assignments (2500) ---
echo ""
echo "Assigning 2500 users..."
S0=$OK; F0=$FAIL

FIRST=("James" "Mary" "Robert" "Patricia" "John" "Jennifer" "Michael" "Linda" "David" "Elizabeth" "William" "Barbara" "Richard" "Susan" "Joseph" "Jessica" "Thomas" "Sarah" "Chris" "Karen" "Charles" "Lisa" "Daniel" "Nancy" "Matt" "Betty" "Tony" "Maggie" "Mark" "Sandra" "Don" "Ashley" "Steve" "Dorothy" "Paul" "Kim" "Andrew" "Emily" "Josh" "Donna" "Ken" "Michelle" "Kevin" "Carol" "Brian" "Amanda" "George" "Melissa" "Tim" "Deborah" "Ron" "Steph" "Ed" "Rebecca" "Jason" "Sharon" "Jeff" "Laura" "Ryan" "Cynthia" "Jake" "Kate" "Gary" "Amy" "Nick" "Angela" "Eric" "Shirley" "Jon" "Anna" "Larry" "Pam" "Justin" "Emma" "Scott" "Nicole" "Brandon" "Helen" "Ben" "Sam" "Ray" "Christine" "Greg" "Debra" "Frank" "Rachel" "Alex" "Carolyn" "Pat" "Janet" "Jack" "Cath" "Dennis" "Maria" "Jerry" "Heather" "Tyler" "Diane" "Liam" "Olivia")
LAST=("Smith" "Johnson" "Williams" "Brown" "Jones" "Garcia" "Miller" "Davis" "Rodriguez" "Martinez" "Hernandez" "Lopez" "Gonzalez" "Wilson" "Anderson" "Thomas" "Taylor" "Moore" "Jackson" "Martin" "Lee" "Perez" "Thompson" "White" "Harris" "Sanchez" "Clark" "Ramirez" "Lewis" "Robinson" "Walker" "Young" "Allen" "King" "Wright" "Scott" "Torres" "Nguyen" "Hill" "Flores" "Green" "Adams" "Nelson" "Baker" "Hall" "Rivera" "Campbell" "Mitchell" "Carter" "Roberts" "Chen" "Wu" "Li" "Wang" "Zhang" "Liu" "Yang" "Huang" "Zhao" "Zhou" "Kumar" "Singh" "Patel" "Sharma" "Gupta" "Joshi" "Shah" "Mehta" "Rao" "Das" "Muller" "Schmidt" "Fischer" "Weber" "Meyer" "Becker" "Schulz" "Hoffmann" "Koch" "Richter" "Wolf" "Braun" "Durand" "Bernard" "Petit" "Robert" "Moreau" "Simon" "Laurent" "Michel" "Lefebvre" "Rossi" "Russo" "Ferrari" "Esposito" "Bianchi" "Romano" "Colombo" "Ricci" "Marino" "Greco")

NF=${#FIRST[@]}; NL=${#LAST[@]}

for i in $(seq 1 2500); do
  fi_idx=$(( (i - 1) % NF ))
  li_idx=$(( (i - 1) / NF % NL ))
  username=$(echo "${FIRST[$fi_idx]:0:1}${LAST[$li_idx]}${i}" | tr '[:upper:]' '[:lower:]')

  gi=$(( i % NUM_GLOBAL ))
  api_post "$API/assignUserRole?type=globalRoles&roleName=${GLOBAL_ROLE_NAMES[$gi]}&user=$username" || true

  if [ $(( i % 10 )) -lt 7 ]; then
    ii=$(( i % NUM_ITEM ))
    api_post "$API/assignUserRole?type=projectRoles&roleName=${ITEM_ROLE_NAMES[$ii]}&user=$username" || true
  fi

  if [ $(( i % 10 )) -lt 3 ]; then
    ai=$(( i % NUM_AGENT ))
    api_post "$API/assignUserRole?type=slaveRoles&roleName=${AGENT_ROLE_NAMES[$ai]}&user=$username" || true
  fi

  if [ $(( i % 500 )) -eq 0 ]; then
    echo "  $i/2500 users..."
  fi
done
echo "  Users done"

# --- Group Assignments (250) ---
echo ""
echo "Assigning 250 groups..."

DEPT=("engineering" "frontend" "backend" "devops" "qa" "security" "data" "mobile" "platform" "infra" "sre" "design" "product" "management" "hr" "finance" "legal" "marketing" "sales" "support" "research" "ml" "analytics" "compliance" "architecture")
LEVEL=("team" "squad" "guild" "chapter" "tribe" "division" "department" "unit" "group" "org")

for i in $(seq 0 249); do
  di=$(( i % ${#DEPT[@]} ))
  li=$(( i / ${#DEPT[@]} % ${#LEVEL[@]} ))
  group="${DEPT[$di]}-${LEVEL[$li]}"

  gi=$(( i % NUM_GLOBAL ))
  api_post "$API/assignGroupRole?type=globalRoles&roleName=${GLOBAL_ROLE_NAMES[$gi]}&group=$group" || true

  if [ $(( i % 3 )) -eq 0 ]; then
    ii=$(( i % NUM_ITEM ))
    api_post "$API/assignGroupRole?type=projectRoles&roleName=${ITEM_ROLE_NAMES[$ii]}&group=$group" || true
  fi

  if [ $(( i % 50 )) -eq 0 ]; then
    echo "  $((i+1))/250 groups..."
  fi
done
echo "  Groups done"

# --- Verify ---
echo ""
echo "=== Verifying ==="
GLOBAL_COUNT=$(curl -sf -u "$JENKINS_USER:$JENKINS_TOKEN" "$API/getAllRoles?type=globalRoles" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
ITEM_COUNT=$(curl -sf -u "$JENKINS_USER:$JENKINS_TOKEN" "$API/getAllRoles?type=projectRoles" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
AGENT_COUNT=$(curl -sf -u "$JENKINS_USER:$JENKINS_TOKEN" "$API/getAllRoles?type=slaveRoles" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
GLOBAL_USERS=$(curl -sf -u "$JENKINS_USER:$JENKINS_TOKEN" "$API/getRoleAssignments?type=globalRoles" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")

echo "  Global roles: $GLOBAL_COUNT"
echo "  Item roles: $ITEM_COUNT"
echo "  Agent roles: $AGENT_COUNT"
echo "  Global role assignments: $GLOBAL_USERS"
echo ""
echo "=== Done ==="
