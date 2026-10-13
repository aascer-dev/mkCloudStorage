# MKCS production deployment

中文部署步骤请阅读：[部署说明.md](部署说明.md)。

The production workflow uses this directory as the Compose deployment bundle. On the deployment host:

1. Copy `deploy/.env.example` to `/opt/mkcs/.env` and fill in every password and public URL.
2. Make sure the self-hosted deployment runner can access Docker and `/opt/mkcs`.
3. Set the repository variable `DEPLOY_ENABLED=true`.
4. Optionally set `DEPLOY_ROOT` when the deployment directory is different from `/opt/mkcs`.

The backend CI verification job runs on GitHub-hosted `ubuntu-latest`. It also reads live JVM metrics through Java Gateway 7.4 using the production JMX options. The backend CD deployment job installs `docker-compose.yml` and `nginx/nginx.conf`, publishes the backend image to GHCR, and updates the backend and Java Gateway services on the self-hosted deployment runner. The frontend workflow publishes the frontend image and updates `frontend` plus `nginx` in the same Compose project.

The GHCR packages may remain private. The workflows authenticate with the workflow `GITHUB_TOKEN`; in each package's settings, grant access to the corresponding repository. For a manual pull on the deployment host, log in with a GitHub token that has `read:packages` (and `repo` when the GitHub repository is private):

```bash
export GHCR_USER=your-github-user
export GHCR_READ_TOKEN=your-read-token
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
docker compose --env-file /opt/mkcs/.env -f /opt/mkcs/docker-compose.yml pull
unset GHCR_READ_TOKEN
```

## Existing development Compose

The external development file at `D:\Docker\container\dev_env\docker-compose.yml` exposes MySQL, Redis Stack, RabbitMQ, and MinIO on the host ports used by the backend's `dev` profile. Use [`.env.dev.example`](.env.dev.example) as the connection-key template when running the backend outside Docker. The real passwords remain in the local ignored `.env`.

The production Compose file intentionally uses internal service names (`mysql`, `redis`, `rabbitmq`, and `minio`) and does not depend on the development project or its host ports.

## Final startup on Linux

Run these commands on the deployment host after the repository workflow has installed the Compose bundle:

```bash
cd /opt/mkcs

# Replace every replace-me value with the development credentials you already use.
# Also replace files.example.com with the real hostname, or localhost for local-only use.
grep -nE 'replace-me|example.com' .env

# Check interpolation before starting anything.
docker compose --env-file .env -f docker-compose.yml config --quiet

# Required only when the GHCR packages are private.
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

# Starts the services on the mkcs_mkcs network. The backend creates shared buckets on first use.
docker compose --env-file .env -f docker-compose.yml pull
docker compose --env-file .env -f docker-compose.yml up -d
docker compose --env-file .env -f docker-compose.yml ps
```

The host needs Docker Engine and the Docker Compose plugin. It does not need Java or Node.js because the CI runner builds the images and the backend image contains its Java 21 runtime. The deployment host only runs the resulting containers.

Every release is also tagged with its commit SHA. To roll back, run `bash /opt/mkcs/rollback.sh sha-<known-good-commit> /opt/mkcs` from a checkout that contains this script, or set `IMAGE_TAG` to that tag and run `docker compose up -d backend frontend nginx` in `/opt/mkcs`.

## Zabbix 7.4 JVM monitoring

The backend enables JMX through `JAVA_TOOL_OPTIONS`, with both the registry and RMI server on port `9010` and the advertised RMI hostname set to `backend`. Java Gateway resolves that name inside the Compose network. JMX authentication and TLS are disabled for this internal connection; the JMX port is not published to the host.

Java Gateway uses `zabbix/zabbix-java-gateway:alpine-7.4-latest` and is published only on `127.0.0.1:10052`, for Zabbix Server running directly on the same host. The optional deployment variables `MKCS_ZABBIX_GATEWAY_BIND_ADDRESS` and `MKCS_ZABBIX_GATEWAY_HOST_PORT` default to those values, including for existing deployment `.env` files.

Configure `/etc/zabbix/zabbix_server.conf`, then restart Zabbix Server:

```ini
JavaGateway=127.0.0.1
JavaGatewayPort=10052
StartJavaPollers=5
```

In the Zabbix UI, add a host monitored by Server, add a JMX interface with DNS name `backend`, select **DNS** for connections, set port `9010`, and link **Generic Java JMX**. Its items use the endpoint `service:jmx:rmi:///jndi/rmi://{HOST.CONN}:{HOST.PORT}/jmxrmi`, which resolves to `backend:9010` with this interface. Do not use the host's IP for this internal JMX interface. See [部署说明.md](部署说明.md#9-zabbix-74-jvm-监控) for verification and troubleshooting.
