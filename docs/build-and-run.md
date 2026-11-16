# 构建与运行

## 构建

```sh
# 前置（跨仓顺序）：mse-framework 仓 → mse-modules 仓（open-api/ai-api）→ 本仓
sh build.sh                          # mvn install -DskipTests（模块：mp-api/mp-service/mp-boot）
MSE_RUN_ALL_TESTS=1 sh build.sh
sh build-ui.sh                       # mp-ui
```

## 本地运行（微服务形态，端口 8091）

```sh
# 1) 基础设施（framework 仓单一真源）
cd <兄弟目录>/mse-framework/docker && docker compose up -d mysql redis nacos
# 2) 导入本服务配置（mse-mp.yaml）
CONFIG_DIR=<本仓>/docker/nacos/config sh <framework仓>/docker/nacos/init-configs.sh
# 3) 启动
java -jar mp-boot/target/mp-boot-1.0.0-SNAPSHOT.jar    # 8091
```

网关基线路由 `lb://mse-mp`；公众号服务器回调地址配置为网关入口 `/mp/callback/{tenantId}/{appId}`（URL 自带租户世界段，免会话凭证）。

## 公众号接入

1. 管理端添加公众号（appId/secret，凭证 `mse-` 世界段语义见 AGENTS.md）；Token/AESKey 与公众平台一致。
2. 公众平台服务器配置指向回调 URL；消息加解密/签名（WxSignatures）自动校验。

## 镜像与 K8s

```sh
docker build -f mp-boot/Dockerfile -t mse/mp-boot:1.0.0 .    # context=本仓根
kubectl apply -f <framework仓>/mse-boot/k8s/configmap.yaml
kubectl apply -f mp-boot/k8s/mp.yaml
```

## 前端

```sh
sh build-ui.sh mp-ui    # --base=/sub-apps/mp/
```
