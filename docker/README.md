# docker/ —— MP 镜像资产与服务配置

本目录只含 MP 自有内容。**基础设施与共享 Nacos 基线配置的单一真源在 mse-framework 仓 `docker/`**。

## 内容

- `nacos/config/mse-mp.yaml`：本服务专属 Data ID 留档（端口 8091 / 业务参数）
- `mp-boot/Dockerfile`：源码多阶段构建（context=**本仓根**，MODULE=mp-boot，PORT=8091）
- `mp-boot/k8s/mp.yaml`：Deployment + Service 清单（复用 framework 仓 `mse-boot/k8s/configmap.yaml` 的 mse-config）

## 本地运行（微服务形态）

1. **基础设施**：`cd <兄弟目录>/mse-framework/docker && docker compose up -d mysql redis nacos`
2. **导入本服务配置**：`CONFIG_DIR=<本仓>/docker/nacos/config sh <framework仓>/docker/nacos/init-configs.sh`
3. **构建并启动**：`sh build.sh` 后 `java -jar mp-boot/target/mp-boot-1.0.0-SNAPSHOT.jar`（8091）
4. 网关 mse-gateway（48080）基线路由已含 `lb://mse-mp`；公众号回调地址指向网关前缀 `/mp/callback/{tenantId}/{appId}`。

前置跨仓依赖：mse-framework 仓与 mse-modules 仓（ai-api / open-api）先各自 `sh build.sh`。

## 镜像构建

```sh
docker build -f mp-boot/Dockerfile -t mse/mp-boot:1.0.0 .
# 快速通道：本机已 mvn package 后用 framework 仓 mse-boot/Dockerfile.prebuilt（context=模块目录）
```

K8s：`kubectl apply -f mp-boot/k8s/mp.yaml`（先 apply framework 仓 configmap）。
