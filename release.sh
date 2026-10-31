#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: bash release.sh <version>}
VERSION=${VERSION#v}
TAG="v$VERSION"
git check-ref-format "refs/tags/$TAG"

git push origin master
git tag "$TAG" HEAD
git push origin "$TAG"
echo "✅ tag $TAG 已推送，Actions 将自动创建 Release 并触发 JitPack"
