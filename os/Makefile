SHELL := /bin/bash

.PHONY: clean repo setup

CONFIG_FILE := ${PWD}/build/conf/auto.conf
LAYERS_DIR := ${PWD}/layers/

setup:
	@echo "Installing Yocto/OpenEmbedded (Scarthgap) build dependencies..." && \
	sudo apt-get update && \
	sudo apt-get install -y \
		gawk wget git diffstat unzip texinfo gcc build-essential chrpath \
		socat cpio python3 python3-pip python3-pexpect python3-git \
		python3-jinja2 python3-subunit xz-utils debianutils iputils-ping \
		zstd liblz4-tool lz4 file locales libacl1 gettext-base; \
	echo "Enabling en_US.UTF-8 locale (required by BitBake)..." && \
	sudo locale-gen en_US.UTF-8; \
	if ! command -v repo >/dev/null 2>&1; then \
		echo "Installing 'repo' tool to /usr/local/bin..." && \
		sudo curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o /usr/local/bin/repo && \
		sudo chmod a+x /usr/local/bin/repo; \
	else \
		echo "'repo' already installed."; \
	fi

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
