#!/usr/bin/env bash
set -euo pipefail

MVN_CMD="${MVN:-mvn}"
NVD_API_DELAY_MS="${NVD_API_DELAY_MS:-6000}"
NVD_RESULTS_PER_PAGE="${NVD_RESULTS_PER_PAGE:-1000}"
SECURITY_FAIL_ON_ERROR="${SECURITY_FAIL_ON_ERROR:-true}"
NVD_DATAFEED_URL="${NVD_DATAFEED_URL:-}"
HAS_NVD_API_KEY=false

if [ -z "${NVD_API_KEY:-}" ]; then
	echo "WARNING: NVD_API_KEY not set - NVD may throttle or return 503 during updates."
	echo "         Get a free key: https://nvd.nist.gov/developers/request-an-api-key"
else
	HAS_NVD_API_KEY=true
fi

common_flags=(
	-DnvdValidForHours=24
	-DnvdMaxRetryCount=5
	"-DnvdApiDelay=${NVD_API_DELAY_MS}"
	"-DnvdApiResultsPerPage=${NVD_RESULTS_PER_PAGE}"
	-DconnectionTimeout=10000
	-DreadTimeout=120000
	-DversionCheckEnabled=false
	-DnodeAnalyzerEnabled=false
	-DretireJsAnalyzerEnabled=false
	-DnodeAuditAnalyzerEnabled=false
)
if [ "${HAS_NVD_API_KEY}" = "true" ]; then
	common_flags+=(-DnvdApiKeyEnvironmentVariable=NVD_API_KEY)
fi

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
update_args=(
	org.owasp:dependency-check-maven:update-only
	"${common_flags[@]}"
)
if [ "${#extra_flags[@]}" -gt 0 ]; then
	update_args+=("${extra_flags[@]}")
fi
if [ "${#fail_flags[@]}" -gt 0 ]; then
	update_args+=("${fail_flags[@]}")
fi

if "${MVN_CMD}" "${update_args[@]}"; then
	:
else
	update_rc=$?
fi

if [ "${update_rc}" -ne 0 ] && [ "${HAS_NVD_API_KEY}" = "true" ]; then
	echo "WARNING: update-only failed while using NVD_API_KEY; retrying once without API key."
	if NVD_API_KEY= "${MVN_CMD}" "${update_args[@]}"; then
		update_rc=0
	else
		update_rc=$?
	fi
fi

if [ "${update_rc}" -ne 0 ]; then
	if [ "${SECURITY_FAIL_ON_ERROR}" = "false" ]; then
		echo "WARNING: update-only failed with exit code ${update_rc}; continuing with local cache."
	else
		exit "${update_rc}"
	fi
fi

echo "Step 2/2: running dependency scan (autoUpdate=false)..."
check_args=(
	org.owasp:dependency-check-maven:check
	"${common_flags[@]}"
	-DautoUpdate=false
	-DfailBuildOnCVSS=9.0
)
if [ "${#extra_flags[@]}" -gt 0 ]; then
	check_args+=("${extra_flags[@]}")
fi
if [ "${#fail_flags[@]}" -gt 0 ]; then
	check_args+=("${fail_flags[@]}")
fi

"${MVN_CMD}" "${check_args[@]}"
