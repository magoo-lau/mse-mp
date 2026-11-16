#!/bin/sh
# =============================================================================
# 微信公众号（MP） 业务工程构建：mvn install（产物安装到本地/私服仓库）
#
# 前置（跨仓构建顺序，见 AGENTS.md）：
#   1) mse-framework 仓已 sh build.sh（mse-starter-parent / mse-dependencies 经仓库坐标解析）
#   2) 跨仓契约 ai-api / open-api（来自 mse-modules 仓）需该仓先构建安装（1.0.0-SNAPSHOT）
#
# 用法：
#   sh build.sh                       # 缺省跳过测试
#   MSE_RUN_ALL_TESTS=1 sh build.sh   # 放开单元测试
#   MAVEN_ARGS="-s /path/settings.xml" sh build.sh
# =============================================================================
set -eu
cd "$(dirname "$0")"

MVN="mvn -B -ntp ${MAVEN_ARGS:-}"
if [ "${MSE_RUN_ALL_TESTS:-0}" = "1" ]; then
  $MVN install
else
  $MVN install -DskipTests
fi
echo "微信公众号（MP） 构建完成"
