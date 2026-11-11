# Kubernetes

```
base/            The payment API: Deployment, Service, PodDisruptionBudget, ServiceAccount, config.
                 The same in every environment.
local/app/       Overlay for the local kind cluster: namespace, image tag, infrastructure addresses.
local/infra/     DEVELOPMENT ONLY: single-instance MySQL, Redis, Kafka and the simulated bank.
local/deploy.sh  Creates the cluster (if needed) and deploys everything, in order.
```

## Run it locally

Needs Docker, [kind](https://kind.sigs.k8s.io/) and kubectl.

```bash
cp k8s/local/secrets.env.example k8s/local/secrets.env   # then fill in the values
./k8s/local/deploy.sh

kubectl -n payment-api port-forward svc/payment-api 8080:80            # the API
kubectl -n payment-api port-forward deployment/payment-api 8081:8081   # health and metrics
```

Run `deploy.sh` again after a code change: it rebuilds the image and rolls the pods.
To remove everything: `kind delete cluster --name payment-api`.

## What the app manifests do

- **Two replicas.** Kafka splits the topic's partitions between them, the outbox relay shares
  work through `SELECT ... FOR UPDATE SKIP LOCKED`, and Liquibase's lock table stops both
  migrating the database at once.
- **Probes on the management port (8081).** Startup allows time for migrations; liveness checks
  only the app itself (a database outage must not restart every pod); readiness also needs the
  database and Redis, so the pod leaves the Service while they're unreachable.
- **Graceful shutdown.** A short `preStop` sleep lets the Service stop routing to the pod, then
  Spring finishes in-flight requests and commits Kafka offsets within the 45s grace period.
- **Locked down.** Non-root user, read-only root filesystem (with a writable `/tmp`), no Linux
  capabilities, no service account token, default seccomp profile.
- **Resources.** CPU request but no CPU limit (limits throttle the JVM); memory limit, with the
  heap sized from it (`-XX:MaxRAMPercentage=75` in the image).
- **Config and secrets as environment variables.** The ConfigMap name includes a content hash,
  so changing a value rolls the pods. The `payment-api-secrets` Secret must provide
  `db-username`, `db-password` and `jwt-secret`.

## Before production

- Replace `local/infra` with managed or replicated services (MySQL with backups, a Kafka cluster
  with at least three brokers, Redis), and write an overlay pointing the ConfigMap at them.
- Keep secrets out of files: use a secret manager (e.g. External Secrets Operator or Sealed
  Secrets) to create `payment-api-secrets`.
- Push the image to a registry with an immutable tag (e.g. the Git commit), not `local`.
- Add an Ingress or Gateway with TLS in front of the Service, a HorizontalPodAutoscaler, and
  a Prometheus scrape config (the pods carry `prometheus.io/*` annotations).
- Lower `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` and enable OTLP export to your collector.
