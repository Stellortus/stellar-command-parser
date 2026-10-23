#!/usr/bin/env bash
git update-index --chmod=+x gradlew
set -e
VERSION=$1
git push origin master
git tag "v$VERSION"
git push origin "v$VERSION"
echo "✅ tag v$VERSION 已推送，Actions 将自动创建 Release 并触发 JitPack"