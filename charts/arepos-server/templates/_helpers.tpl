{{/*
Expand the name of the chart.
*/}}
{{- define "arepos-server.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "arepos-server.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "arepos-server.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "arepos-server.labels" -}}
helm.sh/chart: {{ include "arepos-server.chart" . }}
{{ include "arepos-server.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "arepos-server.selectorLabels" -}}
app.kubernetes.io/name: {{ include "arepos-server.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "arepos-server.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "arepos-server.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
PostgreSQL resource names (respect fullnameOverride/nameOverride).
*/}}
{{- define "arepos-server.postgresql.fullname" -}}
{{- printf "%s-postgresql" (include "arepos-server.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
MinIO resource names.
*/}}
{{- define "arepos-server.minio.fullname" -}}
{{- printf "%s-minio" (include "arepos-server.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Cerbos resource names.
*/}}
{{- define "arepos-server.cerbos.fullname" -}}
{{- printf "%s-cerbos" (include "arepos-server.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "arepos-server.cerbos.config.fullname" -}}
{{- printf "%s-cerbos-config" (include "arepos-server.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "arepos-server.cerbos.policies.fullname" -}}
{{- printf "%s-cerbos-policies" (include "arepos-server.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "arepos-server.cerbos.selectorLabels" -}}
app.kubernetes.io/name: cerbos
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
