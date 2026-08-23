PYTHON ?= python3
JAVA ?= java
UPSTREAM_APK ?= upstream.apk
TOOLS_DIR ?= .tools
WORK_DIR ?= .work
APKTOOL := $(TOOLS_DIR)/apktool.jar
DECODED_DIR := $(WORK_DIR)/upstream-decoded
AUDIT_JSON := $(WORK_DIR)/baseline-audit.json

.PHONY: bootstrap decode audit test

bootstrap:
	bash ./scripts/bootstrap-tools.sh

decode: bootstrap
	test -f "$(UPSTREAM_APK)"
	mkdir -p "$(WORK_DIR)" "$(TOOLS_DIR)/apktool-framework"
	$(JAVA) -jar "$(APKTOOL)" d --force \
		--frame-path "$(TOOLS_DIR)/apktool-framework" \
		--output "$(DECODED_DIR)" "$(UPSTREAM_APK)"

audit: decode
	$(PYTHON) scripts/audit_apk.py \
		--apk "$(UPSTREAM_APK)" \
		--manifest "$(DECODED_DIR)/AndroidManifest.xml" \
		--signatures config/tracker-signatures.json \
		--output "$(AUDIT_JSON)"
	$(PYTHON) -m json.tool "$(AUDIT_JSON)" >/dev/null

test:
	$(PYTHON) -m unittest discover -s tests -v
