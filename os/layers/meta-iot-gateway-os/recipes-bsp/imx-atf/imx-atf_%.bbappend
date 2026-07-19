FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# By default UART4's RDC (Resource Domain Controller) master domain and
# peripheral access permission are locked to the Cortex-M4, independent
# of the Linux devicetree - see the patch for the full explanation and
# the NXP reference this follows. M4 firmware is not used anywhere on
# this board, so freeing UART4 for the A53/Linux side is safe here.
SRC_URI:append:verdin-imx8mm = " \
    file://0001-imx8mm-assign-UART4-RDC-domain-to-A53.patch \
"
