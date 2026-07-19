# We don't ship any devicetree overlays (see gateway-os-image.bb -
# IMAGE_BOOT_FILES:remove for overlays/*  and overlays.txt), so skip the
# boot script's overlay-loading step entirely. Without this, boot.cmd's
# load_overlays_file/apply_overlays chain fails on the now-missing files
# and - since bootcmd_run chains everything with && - aborts the whole
# boot instead of just continuing without overlays.
do_compile:append() {
    # Relative path, matching the base recipe's own `> boot.cmd` redirect -
    # both run in the same task's working directory.
    sed -i '1i setenv skip_fdt_overlays 1' boot.cmd
}
