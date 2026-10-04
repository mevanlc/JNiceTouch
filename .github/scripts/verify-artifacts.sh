#!/usr/bin/env bash
set -euo pipefail

version=${1:?Usage: verify-artifacts.sh VERSION}
if [[ "$version" != "0.0.0-SNAPSHOT" && ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    printf 'Invalid artifact version: %s\n' "$version" >&2
    exit 1
fi
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
binary_jar="$repo_dir/lib/build/libs/jnicetouch-$version.jar"
sources_jar="$repo_dir/lib/build/libs/jnicetouch-$version-sources.jar"
javadoc_jar="$repo_dir/lib/build/libs/jnicetouch-$version-javadoc.jar"
native_member=META-INF/native/macos-universal/libjnicetouch.dylib
pom="$repo_dir/lib/build/publications/maven/pom-default.xml"

for coordinate in 'groupId=io.github.mevanlc' 'artifactId=jnicetouch' "version=$version"; do
    element=${coordinate%%=*}
    actual=$(xmllint --xpath "string(/*[local-name()='project']/*[local-name()='$element'])" "$pom")
    if [[ "$actual" != "${coordinate#*=}" ]]; then
        printf 'Unexpected Maven %s: %s\n' "$element" "$actual" >&2
        exit 1
    fi
done

for artifact in "$binary_jar" "$sources_jar" "$javadoc_jar"; do
    unzip -tq "$artifact"
    unzip -p "$artifact" META-INF/MANIFEST.MF | tr -d '\r' | grep -Fx "Implementation-Version: $version"
    unzip -p "$artifact" META-INF/LICENSE > /dev/null
done
unzip -p "$sources_jar" io/github/mevanlc/jnicetouch/MacTrackpadEvent.java > /dev/null
unzip -p "$javadoc_jar" io.github.mevanlc.jnicetouch/io/github/mevanlc/jnicetouch/MacTrackpadEvent.html > /dev/null

artifact_temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/jnicetouch-artifact.XXXXXX")
trap 'rm -rf "$artifact_temp_dir"' EXIT
unzip -qq -j "$binary_jar" "$native_member" -d "$artifact_temp_dir"
architectures=$(lipo -archs "$artifact_temp_dir/libjnicetouch.dylib")
for architecture in arm64 x86_64; do
    if [[ " $architectures " != *" $architecture "* ]]; then
        printf 'Native library is missing architecture: %s\n' "$architecture" >&2
        exit 1
    fi
done
printf 'Verified JNiceTouch %s artifacts and universal native library.\n' "$version"
