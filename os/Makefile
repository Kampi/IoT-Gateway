SHELL := /bin/bash

.PHONY: clean repo

CONFIG_FILE := ${PWD}/build/conf/auto.conf
LAYERS_DIR := ${PWD}/layers/

clean:
	@echo "Cleaning up..." && \
	rm -rf .repo export; \
	if [ -d "./build" ]; then \
		rm -rf ./build/tmp*;\
		rm -rf ./build/deploy*;\
	fi

repo:
	@echo "Cloning Toradex BSP..." && \
	repo init -u https://git.toradex.com/toradex-manifest.git  -b scarthgap-7.x.y -m tdxref/default.xml && \
	mkdir -p .repo/local_manifests && \
	envsubst < local_manifest.xml > .repo/local_manifests/local_manifest.xml && \
	echo "Syncing repo..." && \
	repo sync; \
	echo "Removing local manifest file..."; \
	rm -f .repo/local_manifests/local_manifest.xml
