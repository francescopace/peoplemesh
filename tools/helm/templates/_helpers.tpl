{{/*
Expand the name of the chart.
*/}}
{{- define "peoplemesh.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "peoplemesh.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Chart label.
*/}}
{{- define "peoplemesh.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels.
*/}}
{{- define "peoplemesh.labels" -}}
helm.sh/chart: {{ include "peoplemesh.chart" . }}
app.kubernetes.io/name: {{ include "peoplemesh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels.
*/}}
{{- define "peoplemesh.selectorLabels" -}}
app.kubernetes.io/name: {{ include "peoplemesh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
ConfigMap name.
*/}}
{{- define "peoplemesh.configMapName" -}}
{{- if .Values.config.name -}}
{{- .Values.config.name -}}
{{- else -}}
{{- printf "%s-config" (include "peoplemesh.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/*
Secret name.
*/}}
{{- define "peoplemesh.secretName" -}}
{{- if .Values.secret.name -}}
{{- .Values.secret.name -}}
{{- else -}}
{{- printf "%s-secrets" (include "peoplemesh.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/*
PostgreSQL service name.
*/}}
{{- define "peoplemesh.postgresqlServiceName" -}}
{{- if .Values.postgresql.service.name -}}
{{- .Values.postgresql.service.name -}}
{{- else -}}
postgresql
{{- end -}}
{{- end -}}

{{/*
Docling service name.
*/}}
{{- define "peoplemesh.doclingServiceName" -}}
{{- if .Values.docling.service.name -}}
{{- .Values.docling.service.name -}}
{{- else -}}
docling
{{- end -}}
{{- end -}}

{{/*
Ollama service name.
*/}}
{{- define "peoplemesh.ollamaServiceName" -}}
{{- if .Values.ollama.service.name -}}
{{- .Values.ollama.service.name -}}
{{- else -}}
ollama
{{- end -}}
{{- end -}}
