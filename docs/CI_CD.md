# CI/CD 部署说明

## GitHub Secrets 清单

| Secret 名 | 用于 | 必填 |
|---|---|---|
| `DOCKER_REGISTRY` | 容器镜像仓库地址（阿里云 / Docker Hub） | ✅ |
| `DOCKER_NAMESPACE` | 镜像仓库命名空间 | ✅ |
| `DOCKER_USERNAME` | Registry 账号 | ✅ |
| `DOCKER_PASSWORD` | Registry 密码或 access token | ✅ |
| `DEPLOY_HOST` | 生产服务器 IP | ✅ |
| `DEPLOY_USER` | SSH 登录用户 | ✅ |
| `DEPLOY_SSH_KEY` | SSH 私钥 PEM 内容 | ✅ |
| `WEAPP_APPID` | 微信小程序 appid | ✅（如用 CI 上传体验版） |
| `WEAPP_PRIVATE_KEY` | 小程序上传密钥内容 | ✅ |
| `UNIAPP_API_BASE_URL` | prod 后端 URL（https） | ✅ |
| `JWT_SECRET_FOR_TEST` | CI 测试用 jwt secret | 可选 |

## 工作流

| workflow 文件 | 触发 | 作用 |
|---|---|---|
| `test.yml` | PR + push 到 main/phase* | 跑测试 + admin/uniapp 构建 |
| `backend-admin-deploy.yml` | push main 或手工 | 构建 push 镜像 + SSH 部署 |
| `uniapp-experience.yml` | push main 改 hardware-mall-uniapp/ 或手工 | 构建小程序 + 体验版上传 |

## 服务器预备文档

1. 创建 deploy 用户并加入 docker 组
2. 把 GitHub Actions 的 public key 加入 deploy 用户 `~/.ssh/authorized_keys`
3. clone repo 到 `/opt/hardware-mall`
4. 配 `.env` 真实环境变量（导入 `.env.example` 模板）
5. Prometheus / Grafana 容器（可选，自用项目可省）

## Rollback

```bash
ssh deploy@server
cd /opt/hardware-mall
export IMAGE_TAG=<上一个稳定 commit sha>
export DOCKER_REGISTRY=<你的仓库地址>
export DOCKER_NAMESPACE=<你的命名空间>
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```
