SHELL := /bin/bash

.PHONY: clean repo setup dtb

CONFIG_FILE := ${PWD}/build/conf/auto.conf
LAYERS_DIR := ${PWD}/layers/
KERNEL_PV := 6.6.142+git
KERNEL_SRC := ${PWD}/build/tmp/work-shared/verdin-imx8mm/kernel-source
KERNEL_BUILD := ${PWD}/build/tmp/work/verdin_imx8mm-poky-linux/linux-toradex/${KERNEL_PV}/build
DTS_FILE := iot-gateway-imx8mm.dts
DTS_LAYER_PATH := ${PWD}/layers/meta-iot-gateway-os/recipes-kernel/linux/linux-toradex/${DTS_FILE}
DTB_TARGET := freescale/iot-gateway-imx8mm.dtb

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

dtb:
	@echo "Syncing ${DTS_FILE} into the kernel source tree..." && \
	cp ${DTS_LAYER_PATH} ${KERNEL_SRC}/arch/arm64/boot/dts/freescale/ && \
	echo "Building ${DTB_TARGET}..." && \
	DTC_FLAGS=-@ $(MAKE) -C ${KERNEL_SRC} ARCH=arm64 O=${KERNEL_BUILD} ${DTB_TARGET} && \
	echo "Done: ${KERNEL_BUILD}/arch/arm64/boot/dts/${DTB_TARGET}"

repo:
	@echo "Cloning Toradex BSP..." && \
	repo init -u https://git.toradex.com/toradex-manifest.git  -b scarthgap-7.x.y -m tdxref/default.xml && \
	mkdir -p .repo/local_manifests && \
	envsubst < local_manifest.xml > .repo/local_manifests/local_manifest.xml && \
	echo "Syncing repo..." && \
	repo sync; \
	echo "Removing local manifest file..."; \
	rm -f .repo/local_manifests/local_manifest.xml
