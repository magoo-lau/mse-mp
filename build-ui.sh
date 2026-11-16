#!/bin/sh
# =============================================================================
# 前端全量构建（本仓 UI 工程）：逐工程 npm ci + type-check（--if-present）+ 生产构建。
#
# 用法：sh build-ui.sh [工程目录 ...]     # 缺省构建清单内全部工程
# 环境变量：NPM_FLAGS 透传给 npm（如 --prefer-offline）
#
# mp-ui file: 引用兄弟仓 mse-framework/mse-ui-shared。
# 依赖：mse-ui-shared 经 file: 相对路径引用（$ROOT 上层兄弟目录 mse-framework 仓），
# 仓间同级摆放时开箱可用；CI 中由 workflow 建软链保证路径成立。
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
