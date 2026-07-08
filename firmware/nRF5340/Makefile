SHELL := /bin/bash

# Optional, machine-local toolchain environment (PATH/LD_LIBRARY_PATH/etc.
# for a specific NCS toolchain install). Gitignored - see toolchain.mk.example
# and README.md "Toolchain" for why this is needed and how to set it up.
-include toolchain.mk

# Physical board to build for. Defaults to the stock Nordic dev kit so the
# workspace builds out of the box without any hardware-specific overlay.
BOARD    ?= nrf5340dk/nrf5340/cpunet

# Selects which devicetree overlay under app/boards/ to layer on top of
# BOARD:
#   dk      - stock nRF5340-DK, no changes (default)
#   gateway - custom carrier board
TARGET   ?= dk

# Maps TARGET to the actual overlay file basename under app/boards/. "dk"
# uses Zephyr's own board-qualifier naming convention
# (<board>_<qualifiers>.overlay, "/" -> "_", matching BOARD below) since
# it's a real, exact Zephyr board id - same pattern Nordic uses for its
# own per-board overlays (see nrf/samples/openthread/coprocessor/boards/).
# "gateway" is not a formal Zephyr board (it reuses the DK's SoC-level
# config, see BOARD above), so it just keeps its own short name.
OVERLAY_BASENAME_dk      := nrf5340dk_nrf5340_cpunet
OVERLAY_BASENAME_gateway := gateway
OVERLAY_BASENAME         := $(OVERLAY_BASENAME_$(TARGET))

SAMPLE    := nrf/samples/openthread/coprocessor
BUILD_DIR := build
OVERLAY   := $(CURDIR)/app/boards/$(OVERLAY_BASENAME).overlay
CONF      := $(CURDIR)/app/prj.conf
# Per-target Kconfig fragment, e.g. app/boards/nrf5340dk_nrf5340_cpunet.conf
# enables the heartbeat LED (app/heartbeat_led/) for TARGET=dk only.
BOARD_CONF := $(CURDIR)/app/boards/$(OVERLAY_BASENAME).conf
# Out-of-tree module contributing the heartbeat LED, see
# app/heartbeat_led/zephyr/{CMakeLists.txt,Kconfig}.
HEARTBEAT_MODULE := $(CURDIR)/app/heartbeat_led

# MCUboot bootloader / DFU (see README.md, "DFU / Bootloader"). MCUboot's
# build also gets $(OVERLAY) (the app's own board overlay, not just
# MCUBOOT_OVERLAY). It must use the same physical UART pins/speed/flow
# control as the app to be reachable at all, and that pinctrl/&uart0
# config lives in $(OVERLAY) (e.g. app/boards/gateway.overlay), not in
# the MCUboot-specific one.
SYSBUILD_CONF      := $(CURDIR)/app/sysbuild.conf
MCUBOOT_CONF       := $(CURDIR)/app/mcuboot.conf
MCUBOOT_OVERLAY    := $(CURDIR)/app/boards/mcuboot-$(OVERLAY_BASENAME).overlay
# Single-slot partition table (see app/boards/partitions.overlay) - both
# the app and MCUboot must agree on the same flash layout, so this is
# appended to both images' devicetree overlays below.
PARTITIONS_OVERLAY := $(CURDIR)/app/boards/partitions.overlay
DFU_KEY  ?= app/dfu_signing_key.pem
DFU_PORT ?= /dev/ttyACM0
DFU_BAUD ?= 115200
NRFJPROG_SNR ?=

# App-core companion, DK builds only (see README.md "Heartbeat LED"): the
# net core owns no GPIO pins by default on the nRF5340 - the DK's own
# app-core devicetree only forwards the 4 Spinel UART pins to the net
# core (zephyr/boards/nordic/nrf5340dk/nrf5340_cpuapp_common.dtsi's
# gpio_fwd node), not LED0. Without app-core firmware that forwards the
# rest, heartbeat_led's gpio_pin_toggle_dt() silently has no effect.
# nrf/samples/nrf5340/empty_app_core is Nordic's own sample for exactly
# this: forwards every GPIO to the net core, then suspends the app core.
ifeq ($(TARGET),dk)
APPCORE_BOARD     := nrf5340dk/nrf5340/cpuapp
APPCORE_SAMPLE    := nrf/samples/nrf5340/empty_app_core
APPCORE_BUILD_DIR := build_appcore
endif

.PHONY: setup update build flash menuconfig clean pristine dfu-keygen dfu recover

# One-time (or after bumping app/west.yml) workspace setup: fetches the
# nRF Connect SDK + Zephyr + modules as siblings of app/, all git-ignored.
setup:
	@if [ ! -d .west ]; then west init -l app; fi
	$(MAKE) update
	west zephyr-export
	pip3 install -r zephyr/scripts/requirements.txt
	pip3 install -r nrf/scripts/requirements.txt
	pip3 install -r bootloader/mcuboot/scripts/requirements.txt

update:
	west update

# Builds the OpenThread coprocessor (RCP) sample from the nRF Connect SDK
# for BOARD, with our overlay/conf fragments layered on top, plus MCUboot
# as a chain-loaded sysbuild child image.
build: dfu-keygen
	west build -b $(BOARD) -d $(BUILD_DIR) -s $(SAMPLE) --sysbuild -- \
		-DEXTRA_CONF_FILE="$(CONF) $(BOARD_CONF)" \
		-DEXTRA_DTC_OVERLAY_FILE="$(OVERLAY) $(PARTITIONS_OVERLAY)" \
		-DZEPHYR_EXTRA_MODULES=$(HEARTBEAT_MODULE) \
		-DSB_CONF_FILE=$(SYSBUILD_CONF) \
		-DSB_CONFIG_BOOT_SIGNATURE_KEY_FILE=\"$(CURDIR)/$(DFU_KEY)\" \
		-Dmcuboot_EXTRA_CONF_FILE=$(MCUBOOT_CONF) \
		-Dmcuboot_EXTRA_DTC_OVERLAY_FILE="$(OVERLAY) $(MCUBOOT_OVERLAY) $(PARTITIONS_OVERLAY)"
ifeq ($(TARGET),dk)
	west build -b $(APPCORE_BOARD) -d $(APPCORE_BUILD_DIR) -s $(APPCORE_SAMPLE)
endif

# First flash (via SWD/J-Link) writes MCUboot + the signed app image,
# plus the GPIO-forwarding app-core companion for TARGET=dk (see above).
# Pass extra west/nrfjprog flags via FLASH_ARGS, e.g. a stuck board with
# AP-protect enabled after an interrupted recover:
#   make flash FLASH_ARGS='--recover --tool-opt="--clockspeed 500"'
FLASH_ARGS ?=
flash:
	west flash -d $(BUILD_DIR) $(FLASH_ARGS)
ifeq ($(TARGET),dk)
	west flash -d $(APPCORE_BUILD_DIR) $(FLASH_ARGS)
endif

# Generates the DFU image-signing key on first use. Back this file up
# somewhere safe outside of git losing it means devices already in the
# field can no longer be updated with a matching signature.
dfu-keygen:
	@if [ ! -f $(DFU_KEY) ]; then \
		echo "Generating DFU signing key at $(DFU_KEY)"; \
		imgtool keygen -k $(DFU_KEY) -t ed25519; \
	fi

# Uploads the most recently built signed image over UART via MCUboot's
# serial recovery (mcumgr). The nRF5340 must be held in recovery mode
# (mcuboot-button0 pressed/held low at reset) before running this.
dfu:
	@img=$$(find $(BUILD_DIR) -name zephyr.signed.bin ! -path "*/mcuboot/*" | head -n1); \
	if [ -z "$$img" ]; then echo "No signed image found under $(BUILD_DIR) - run 'make build' first."; exit 1; fi; \
	echo "Uploading $$img via $(DFU_PORT)"; \
	mcumgr --conntype serial --connstring "dev=$(DFU_PORT),baud=$(DFU_BAUD)" image upload "$$img"; \
	mcumgr --conntype serial --connstring "dev=$(DFU_PORT),baud=$(DFU_BAUD)" reset

menuconfig:
	west build -t menuconfig -d $(BUILD_DIR)

# Recovers a board stuck after a failed/interrupted flash (JLink HALT or
# erase errors from `make flash`). Pass NRFJPROG_SNR=<serial> if you have
# more than one board connected (see the serial number nrfjprog/west
# flash prints, e.g. "Using board 1050051093").
recover:
	nrfjprog --recover $(if $(NRFJPROG_SNR),--snr $(NRFJPROG_SNR)) --clockspeed 500

# Removes the build output only.
clean:
	rm -rf $(BUILD_DIR) $(APPCORE_BUILD_DIR)

# Removes the build output AND the fetched west workspace (nrf/, zephyr/,
# modules/, ...). Next build will need `make setup` again.
pristine: clean
	rm -rf .west zephyr nrf modules bootloader nrfxlib test tools mbedtls bsim
