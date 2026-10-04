#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for version in 0.0.0 0.1.0 1.20.300 10.0.0; do
    actual=$(bash "$script_dir/release-version.sh" "v$version")
    if [[ "$actual" != "$version" ]]; then
        printf 'Wrong version extracted from v%s: %s\n' "$version" "$actual" >&2
        exit 1
    fi
done
for tag in v v1.2 1.2.3 v01.2.3 v1.02.3 v1.2.03 v1.2.3-SNAPSHOT v1.2.3-rc.1 v1.2.3+build v-1.2.3; do
    if bash "$script_dir/release-version.sh" "$tag" > /dev/null 2>&1; then
        printf 'Invalid release tag was accepted: %s\n' "$tag" >&2
        exit 1
    fi
done
printf 'Release tag validation tests passed.\n'
