# Milliseconds between NVD API requests (throttle to reduce 503/rate-limit pressure).
NVD_API_DELAY_MS ?= 6000
# Smaller pages are gentler on NVD API (max accepted by NVD is 2000).
NVD_RESULTS_PER_PAGE ?= 1000
SECURITY_FAIL_ON_ERROR ?= true
NVD_DATAFEED_URL ?=
export NVD_API_KEY

.PHONY: test-security

# Two-step security scan:
#   1) dependency-check:update-only (download/update vulnerability DB)
#   2) dependency-check:check with autoUpdate=false (scan using local DB)
#
# Usage examples:
#   make test-security
#   NVD_API_KEY=<your-key> make test-security
#   make test-security NVD_API_DELAY_MS=8000 NVD_RESULTS_PER_PAGE=500
#   make test-security SECURITY_FAIL_ON_ERROR=false
#   make test-security NVD_DATAFEED_URL=https://mirror.example.com/nvdcve-{0}.json.gz
test-security:
	@MVN="$(MVN)" \
	NVD_API_DELAY_MS="$(NVD_API_DELAY_MS)" \
	NVD_RESULTS_PER_PAGE="$(NVD_RESULTS_PER_PAGE)" \
	SECURITY_FAIL_ON_ERROR="$(SECURITY_FAIL_ON_ERROR)" \
	NVD_DATAFEED_URL="$(NVD_DATAFEED_URL)" \
	bash tools/make/security/test-security.sh
