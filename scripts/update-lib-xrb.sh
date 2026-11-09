#!/usr/bin/env bash
# Regenerates the exclude list in src/assembly/lib-xrb.xml:
#   target/lib-xrb = runtime dependencies of the writer MINUS the libraries the xrb WAR already ships.
# Only the overlap is written (this repository is public, xrb's full library list stays out of it).
# Rerun after changing the writer's dependencies or when the libraries of xrb change.
#
# Usage: scripts/update-lib-xrb.sh <xrb-dependency-list.txt> [label]
#
# Create the list in the xrb repository (Jakarta profile = default of mvn-local.sh):
#   ./scripts/mvn-local.sh dependency:list -DincludeScope=runtime -DoutputFile=/tmp/xrb-libs.txt
# The xrb WAR has no packaging excludes, so its runtime dependencies are exactly its WEB-INF/lib.
# MVN selects the Maven executable used for the writer (default: mvn).
set -euo pipefail
export LC_ALL=C

xrb_list="${1:?usage: $0 <xrb-dependency-list.txt> [label]}"
label="${2:-xrb}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
descriptor="$root/src/assembly/lib-xrb.xml"

# "   group:artifact:type[:classifier]:version:scope -- module ..." -> "group:artifact"
ga() { awk '$1 ~ /:(compile|runtime)$/ { split($1, p, ":"); print p[1] ":" p[2] }' "$1" | sort -u; }

writer_list="$(mktemp)"
tmp="$(mktemp)"
trap 'rm -f "$writer_list" "$tmp"' EXIT

(cd "$root" && "${MVN:-mvn}" -B -q -ntp dependency:list -DincludeScope=runtime -DoutputFile="$writer_list" >/dev/null)

xrb_count="$(ga "$xrb_list" | grep -c . || true)"
if [[ "$xrb_count" -eq 0 ]]; then
    echo "No compile/runtime dependencies found in $xrb_list" >&2
    exit 1
fi
excludes="$(comm -12 <(ga "$writer_list") <(ga "$xrb_list"))"
count="$(printf '%s' "$excludes" | grep -c . || true)"

{
    sed -n '1,/BEGIN xrb libs/p' "$descriptor"
    printf '                <!-- %s: %s of its %s libs are also writer dependencies -->\n' "$label" "$count" "$xrb_count"
    if [[ -n "$excludes" ]]; then
        printf '%s\n' "$excludes" | sed 's|.*|                <exclude>&</exclude>|'
    fi
    sed -n '/END xrb libs/,$p' "$descriptor"
} > "$tmp"
cat "$tmp" > "$descriptor"
echo "$descriptor: $count writer libs are shipped by $label and excluded from target/lib-xrb"
