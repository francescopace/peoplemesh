#!/usr/bin/env bash
set -euo pipefail

MVN_CMD="${MVN:-mvn}"
NVD_API_DELAY_MS="${NVD_API_DELAY_MS:-6000}"
NVD_RESULTS_PER_PAGE="${NVD_RESULTS_PER_PAGE:-1000}"
SECURITY_FAIL_ON_ERROR="${SECURITY_FAIL_ON_ERROR:-true}"
NVD_DATAFEED_URL="${NVD_DATAFEED_URL:-}"

if [ -z "${NVD_API_KEY:-}" ]; then
	echo "WARNING: NVD_API_KEY not set - NVD may throttle or return 503 during updates."
	echo "         Get a free key: https://nvd.nist.gov/developers/request-an-api-key"
fi

common_flags=(
	-DnvdValidForHours=24
	-DnvdMaxRetryCount=5
	"-DnvdApiDelay=${NVD_API_DELAY_MS}"
	"-DnvdApiResultsPerPage=${NVD_RESULTS_PER_PAGE}"
	-DnvdApiKeyEnvironmentVariable=NVD_API_KEY
	-DconnectionTimeout=10000
	-DreadTimeout=120000
	-DversionCheckEnabled=false
	-DnodeAnalyzerEnabled=false
	-DretireJsAnalyzerEnabled=false
	-DnodeAuditAnalyzerEnabled=false
)

extra_flags=()
if [ -n "${NVD_DATAFEED_URL}" ]; then
	echo "Using NVD data feed mirror: ${NVD_DATAFEED_URL}"
	extra_flags+=("-DnvdDatafeedUrl=${NVD_DATAFEED_URL}")
fi

fail_flags=()
if [ "${SECURITY_FAIL_ON_ERROR}" = "false" ]; then
	echo "WARNING: SECURITY_FAIL_ON_ERROR=false, upstream update errors will not fail the build."
	fail_flags+=(-DfailOnError=false)
fi

echo "Step 1/2: updating vulnerability database..."
update_rc=0
if "${MVN_CMD}" org.owasp:dependency-check-maven:update-only \
	"${common_flags[@]}" \
	"${extra_flags[@]}" \
	"${fail_flags[@]}"; then
	:
else
	update_rc=$?
fi

if [ "${update_rc}" -ne 0 ]; then
	if [ "${SECURITY_FAIL_ON_ERROR}" = "false" ]; then
		echo "WARNING: update-only failed with exit code ${update_rc}; continuing with local cache."
	else
		exit "${update_rc}"
	fi
fi

echo "Step 2/2: running dependency scan (autoUpdate=false)..."
"${MVN_CMD}" org.owasp:dependency-check-maven:check \
	"${common_flags[@]}" \
	-DautoUpdate=false \
	-DfailBuildOnCVSS=9.0 \
	"${extra_flags[@]}" \
	"${fail_flags[@]}"
