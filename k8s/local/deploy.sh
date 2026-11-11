#!/usr/bin/env bash
# Builds the app and deploys it, with its development infrastructure, to a local kind cluster.
# Safe to run again: it updates what already exists. Needs docker, kind and kubectl.
set -euo pipefail

cd "$(dirname "$0")/../.."

CLUSTER=payment-api
NAMESPACE=payment-api
SECRETS_FILE=k8s/local/secrets.env

if [[ ! -f "$SECRETS_FILE" ]]; then
  echo "Missing $SECRETS_FILE: copy k8s/local/secrets.env.example and fill in the values." >&2
  exit 1
fi

if ! kind get clusters | grep -qx "$CLUSTER"; then
  kind create cluster --config k8s/local/kind-cluster.yaml
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

echo "==> Building and loading the image"
docker build -t payment-api:local .
kind load docker-image payment-api:local --name "$CLUSTER"

echo "==> Namespace, secret and bank rules"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" create secret generic payment-api-secrets \
  --from-env-file="$SECRETS_FILE" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" create configmap bank-mappings \
  --from-file=infra/wiremock/mappings --dry-run=client -o yaml | kubectl apply -f -

echo "==> Infrastructure (development only)"
kubectl apply -k k8s/local/infra
kubectl -n "$NAMESPACE" rollout status statefulset/mysql --timeout=300s
kubectl -n "$NAMESPACE" rollout status statefulset/kafka --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/redis --timeout=120s
kubectl -n "$NAMESPACE" rollout status deployment/bank --timeout=120s

echo "==> Payment API"
kubectl apply -k k8s/local/app
# The image tag stays "local", so restart to pick up the image that was just loaded.
kubectl -n "$NAMESPACE" rollout restart deployment/payment-api
kubectl -n "$NAMESPACE" rollout status deployment/payment-api --timeout=300s

cat <<EOF

Deployed. To call the API:
  kubectl -n $NAMESPACE port-forward svc/payment-api 8080:80
Health and metrics (management port, not exposed by the Service):
  kubectl -n $NAMESPACE port-forward deployment/payment-api 8081:8081
EOF
