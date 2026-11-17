#!/bin/sh
# =============================================================================
# 前端全量构建（本仓 UI 工程）：逐工程 npm ci + type-check（--if-present）+ 生产构建。
#
# 用法：sh build-ui.sh [工程目录 ...]     # 缺省构建清单内全部工程
# 环境变量：NPM_FLAGS 透传给 npm（如 --prefer-offline）
#
# mse-ui-shared 为 npm git 依赖（github:magoo-lau/mse-ui-shared#release/1.0.0，
# 独立仓；根 .npmrc 已放行 allow-git），npm ci 原生解析安装，无需额外步骤。
# =============================================================================
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

NPM="npm ${NPM_FLAGS:-}"

ALL_FRONTENDS="
mp-ui
"

TARGETS=${*:-$ALL_FRONTENDS}


failed=""
total=0
for dir in $TARGETS; do
  [ -f "$dir/package.json" ] || { echo "!! 跳过 $dir（无 package.json）"; continue; }
  total=$((total + 1))
  echo "=== [$dir] npm ci ==="
  if (cd "$dir" && $NPM ci --no-audit --no-fund)       && echo "=== [$dir] type-check（如登记）==="       && (cd "$dir" && $NPM run type-check --if-present)       && echo "=== [$dir] build（如登记）==="       && (cd "$dir" && $NPM run build --if-present); then
    echo "--- [$dir] OK"
  else
    echo "--- [$dir] FAILED"
    failed="$failed $dir"
  fi
done

echo ""
if [ -n "$failed" ]; then
  echo "前端构建失败（$failed ），共 $total 个工程" >&2
  exit 1
fi
echo "前端全部构建通过（$total 个工程）"
