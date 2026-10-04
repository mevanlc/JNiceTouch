#!/usr/bin/env bash
set -euo pipefail

tag=${1:?Usage: release-version.sh vX.Y.Z}
if [[ ! "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    printf 'Invalid release tag: %s. Expected vX.Y.Z without qualifiers or leading zeros.\n' "$tag" >&2
    exit 1
fi
printf '%s\n' "${tag#v}"
