SHELL := /bin/bash

# Machine-local toolchain environment (PATH/PYTHONHOME/... for the NCS
# toolchain matching NCS_DIR). Gitignored - see toolchain.mk.example.
-include toolchain.mk

# Matter needs the full nRF Connect SDK (connectedhomeip, openthread, ...),
# several GB. Instead of a per-project west workspace we build against an
# existing NCS install (Toolchain Manager / nrfutil sdk-manager layout).
NCS_DIR ?= $(HOME)/ncs/v3.3.4
export ZEPHYR_BASE := $(NCS_DIR)/zephyr
WEST := cd $(NCS_DIR) && west

BOARD     ?= nrf54l15dk/nrf54l15/cpuapp
APP_DIR   := $(CURDIR)/app
BUILD_DIR := $(CURDIR)/build

# debug (default): logs + Matter shell on the DK's VCOM
# release:         prj_release.conf, no logs/shell, watchdog + LTO
VARIANT ?= debug
ifeq ($(VARIANT),release)
VARIANT_ARGS := -DFILE_SUFFIX=release
endif

# J-Link serial of the DK, only needed with several probes attached.
SNR ?=

# Serial console (debug build). The DK's VCOM1 carries the app UART.
PORT ?= /dev/ttyACM1
BAUD ?= 115200

.PHONY: build flash erase-flash menuconfig console qrcode clean

build:
	$(WEST) build -b $(BOARD) -d $(BUILD_DIR) -s $(APP_DIR) --sysbuild -- $(VARIANT_ARGS)

# First flash: writes MCUboot, app and factory data (pairing code,
# discriminator, DAC) and wipes everything else incl. the Matter fabric and
# Thread credentials in settings storage (= full factory reset).
erase-flash:
	$(WEST) flash -d $(BUILD_DIR) --erase $(if $(SNR),--dev-id $(SNR))

# Later flashes: only the signed app image (MCUboot primary slot). MCUboot
# write-protects its own RRAM region at runtime, so overwriting merged.hex
# fails with "protected RRAMC region". Factory data and settings (Matter
# fabric, Thread dataset) stay untouched, no re-commissioning needed.
flash:
	nrfutil device program $(if $(SNR),--serial-number $(SNR)) \
		--firmware $(BUILD_DIR)/mcuboot_primary_app.hex \
		--options chip_erase_mode=ERASE_RANGES_TOUCHED_BY_FIRMWARE,reset=RESET_SYSTEM

menuconfig:
	$(WEST) build -d $(BUILD_DIR) -t menuconfig

console:
	picocom -b $(BAUD) $(PORT)

# Prints the onboarding QR code payload (MT:...) generated with the
# factory data. A scannable PNG is next to it: build/app/zephyr/factory_data.png
qrcode:
	@cat $(BUILD_DIR)/app/zephyr/factory_data.txt 2>/dev/null || echo "No factory data - run 'make build' first."
	@echo; echo "QR code image: $(BUILD_DIR)/app/zephyr/factory_data.png"

clean:
	rm -rf $(BUILD_DIR)
