#!/bin/sh
#
# Проверка Helm-чарта arepos-server: lint, dry-run template через kubectl и dry-run install.
# Запускать из корня репозитория; требуются helm и kubectl.
#
# Примеры:
#   ./scripts/helmCheck.sh
#
cd charts && helm lint ./arepos-server && helm template ./arepos-server --values arepos-server/values.yaml | kubectl apply --dry-run=client -f - && helm install arepos-server-0.0.1 ./arepos-server --dry-run -n arch --debug --values arepos-server/values.yaml
