# Recipe created by recipetool
# This is the basis of a recipe and may need further editing in order to be fully functional.
# (Feel free to remove these comments when editing.)

SUMMARY = "Zigbee to MQTT bridge using Zigbee-herdsman"
HOMEPAGE = "https://koenkk.github.io/zigbee2mqtt"
# WARNING: the following LICENSE and LIC_FILES_CHKSUM values are best guesses - it is
# your responsibility to verify that the values are complete and correct.
#
# The following license files were not able to be identified and are
# represented as "Unknown" below, you will need to check them yourself:
#   LICENSE
#   node_modules/@colors/colors/LICENSE
#   node_modules/argparse/LICENSE
#   node_modules/bl/LICENSE.md
#   node_modules/bl/node_modules/readable-stream/LICENSE
#   node_modules/bl/node_modules/string_decoder/LICENSE
#   node_modules/bonjour-service/LICENSE
#   node_modules/concat-stream/node_modules/readable-stream/LICENSE
#   node_modules/debounce/license
#   node_modules/fast-uri/LICENSE
#   node_modules/glob/LICENSE.md
#   node_modules/humanize-duration/LICENSE.txt
#   node_modules/jszip/LICENSE.markdown
#   node_modules/minimatch/LICENSE.md
#   node_modules/mqtt-packet/LICENSE.md
#   node_modules/mqtt/LICENSE.md
#   node_modules/mqtt/node_modules/readable-stream/LICENSE
#   node_modules/mqtt/node_modules/string_decoder/LICENSE
#   node_modules/package-json-from-dist/LICENSE.md
#   node_modules/readable-stream/LICENSE
#   node_modules/slip/GPL-LICENSE.txt
#   node_modules/source-map/LICENSE
#   node_modules/string_decoder/LICENSE
#   node_modules/typedarray/LICENSE
#   node_modules/winston-transport/node_modules/readable-stream/LICENSE
#   node_modules/winston/node_modules/readable-stream/LICENSE
#   node_modules/zigbee-on-host/LICENSE
#   node_modules/zigbee2mqtt-frontend/LICENSE
#   node_modules/zigbee2mqtt-windfront/LICENSE
#
# NOTE: multiple licenses have been detected; they have been separated with &
# in the LICENSE value for now since it is a reasonable assumption that all
# of the licenses apply. If instead there is a choice between the multiple
# licenses then you should change the value to separate the licenses with |
# instead of &. If there is any doubt, check the accompanying documentation
# to determine which situation is applicable.
LICENSE = "GPL-3.0-only & 0BSD & BlueOak-1.0.0 & BSD-3-Clause & ISC & MIT"

# Sub-packages whose license could not be auto-detected are mapped to the
# nearest available file so do_create_spdx can extract license text.
NO_GENERIC_LICENSE[Unknown] = "LICENSE"
LIC_FILES_CHKSUM = "file://LICENSE;md5=84dcc94da3adb52b53ae4fa38fe49e5d \
                    file://node_modules/@babel/runtime/LICENSE;md5=b1d0cd283a346e919abb3beeb018279d \
                    file://node_modules/@colors/colors/LICENSE;md5=12d99be4215ea44df59ce831ed79d258 \
                    file://node_modules/@dabh/diagnostics/LICENSE;md5=c306c558012a48012189a646e0635014 \
                    file://node_modules/@date-fns/tz/LICENSE.md;md5=d7192ed91383719bfdf73f98acdf3125 \
                    file://node_modules/@leichtgewicht/ip-codec/LICENSE;md5=0ab3d176587f714c0377659a2771ac38 \
                    file://node_modules/@serialport/bindings-cpp/LICENSE;md5=e2bc0aa64d278eb2b6ddeb692e294945 \
                    file://node_modules/@serialport/bindings-cpp/node_modules/debug/LICENSE;md5=d85a365580888e9ee0a01fb53e8e9bf0 \
                    file://node_modules/@serialport/bindings-interface/LICENSE;md5=e51623d7e3b251d815956df4ea40c6b3 \
                    file://node_modules/@serialport/parser-delimiter/LICENSE;md5=e2bc0aa64d278eb2b6ddeb692e294945 \
                    file://node_modules/@serialport/parser-readline/LICENSE;md5=e2bc0aa64d278eb2b6ddeb692e294945 \
                    file://node_modules/@serialport/stream/LICENSE;md5=e2bc0aa64d278eb2b6ddeb692e294945 \
                    file://node_modules/@serialport/stream/node_modules/debug/LICENSE;md5=d85a365580888e9ee0a01fb53e8e9bf0 \
                    file://node_modules/@so-ric/colorspace/LICENSE.md;md5=9eb79a66ff768a926dbe298b2b89a3af \
                    file://node_modules/@types/node/LICENSE;md5=d4a904ca135bb7bc912156fee12726f0 \
                    file://node_modules/@types/readable-stream/LICENSE;md5=d4a904ca135bb7bc912156fee12726f0 \
                    file://node_modules/@types/triple-beam/LICENSE;md5=d4a904ca135bb7bc912156fee12726f0 \
                    file://node_modules/@types/ws/LICENSE;md5=d4a904ca135bb7bc912156fee12726f0 \
                    file://node_modules/abort-controller/LICENSE;md5=86a65c5c19c672ee4cd52492495f1d16 \
                    file://node_modules/ajv/LICENSE;md5=5ed8db7ae36f56c8a5cfc218c41ac926 \
                    file://node_modules/argparse/LICENSE;md5=203a6dbc802ee896020a47161e759642 \
                    file://node_modules/async/LICENSE;md5=73f63c1fa4541b2f2f34d77140ebe89d \
                    file://node_modules/balanced-match/LICENSE.md;md5=4b364fd4a3785736f483d59601f3bfcf \
                    file://node_modules/base64-js/LICENSE;md5=ea9187ca93cdc4f71219d1675712e908 \
                    file://node_modules/bind-decorator/LICENSE;md5=5015d8872d092147348c347928ab10cb \
                    file://node_modules/bindings/LICENSE.md;md5=471723f32516f18ef36e7ef63580e4a8 \
                    file://node_modules/bl/LICENSE.md;md5=455bc3781a009cf9a615e8622138814c \
                    file://node_modules/bl/node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/bl/node_modules/safe-buffer/LICENSE;md5=badd5e91c737e7ffdf10b40c1f907761 \
                    file://node_modules/bl/node_modules/string_decoder/LICENSE;md5=14af51f8c0a6c6e400b53e18c6e5f85c \
                    file://node_modules/bonjour-service/LICENSE;md5=00042654f7b66ce7fb90ac2f5ac22921 \
                    file://node_modules/brace-expansion/LICENSE;md5=2c7557c15d5e18815804912fd9844b8f \
                    file://node_modules/broker-factory/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/buffer-from/LICENSE;md5=46513463e8f7d9eb671a243f0083b2c6 \
                    file://node_modules/buffer/LICENSE;md5=e49e579dbcc02cf1f699deec85fd96f0 \
                    file://node_modules/color-convert/LICENSE;md5=d350335111e859fb3553f0215920b1ef \
                    file://node_modules/color-name/LICENSE;md5=24709b87ff9b56314795932559cb8cac \
                    file://node_modules/color-string/LICENSE;md5=330031db3ec2b47f6e9d7923b8e1f95b \
                    file://node_modules/color/LICENSE;md5=2fdebf76c074642a861bf45bf4a8258e \
                    file://node_modules/commist/LICENSE;md5=9d6c3a74d018cc8b3379ce581bcd28ab \
                    file://node_modules/concat-stream/LICENSE;md5=3ad90c134f824ddfcea611ee1fa567a8 \
                    file://node_modules/concat-stream/node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/core-util-is/LICENSE;md5=6126e36127d20ec0e2f637204a5c68ff \
                    file://node_modules/debounce/license;md5=2d6844a6a64c571115c1f30edd019fbf \
                    file://node_modules/debug/LICENSE;md5=d85a365580888e9ee0a01fb53e8e9bf0 \
                    file://node_modules/depd/LICENSE;md5=ebc30494fd072dc98368da73e1821715 \
                    file://node_modules/dns-packet/LICENSE;md5=42014010547e55bd3bcac2fec8c45624 \
                    file://node_modules/ee-first/LICENSE;md5=c8d3a30332ecb31cfaf4c0a06da18f5c \
                    file://node_modules/enabled/LICENSE;md5=c306c558012a48012189a646e0635014 \
                    file://node_modules/encodeurl/LICENSE;md5=272621efa0ff4f18a73221e49ab60654 \
                    file://node_modules/escape-html/LICENSE;md5=f8746101546eeb9e4f6de64bb8bdf595 \
                    file://node_modules/etag/LICENSE;md5=6e8686b7b13dd7ac8733645a81842c4a \
                    file://node_modules/event-target-shim/LICENSE;md5=893385a31dda2493704bc7f39bc976a0 \
                    file://node_modules/events/LICENSE;md5=d6ec4b5e129a1d757b41126ba3934078 \
                    file://node_modules/express-static-gzip/LICENSE.md;md5=bb134bd5f81dbd6650b1d8c607c8725b \
                    file://node_modules/fast-deep-equal/LICENSE;md5=ea87ade09b9e6da4f2e47904a4ee137b \
                    file://node_modules/fast-unique-numbers/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/fast-uri/LICENSE;md5=8af5960e6ba8759504e48d57bd9b9ccd \
                    file://node_modules/fecha/LICENSE;md5=f4f83e94071a71d564921579ec889591 \
                    file://node_modules/file-uri-to-path/LICENSE;md5=9513dc0b97137379cfabc81b60889174 \
                    file://node_modules/finalhandler/LICENSE;md5=462b10b32bb9175b97944aabef4aa171 \
                    file://node_modules/fn.name/LICENSE;md5=d9584417fe22ac64739ce9eb464ef34c \
                    file://node_modules/fresh/LICENSE;md5=373c2cf0978b37e434394a43b4cbbdb4 \
                    file://node_modules/glob/LICENSE.md;md5=add98d83942d5809bc719262ae40f4ce \
                    file://node_modules/glossy/LICENSE;md5=04598f95a1adcdfb9181e68cbcaea47e \
                    file://node_modules/help-me/LICENSE;md5=f15902492d54d5b2ec098c781019c6dd \
                    file://node_modules/http-errors/LICENSE;md5=607209623abfcc77b9098f71a0ef52f9 \
                    file://node_modules/humanize-duration/LICENSE.txt;md5=f4c62131f879a8445e16a7f265aea635 \
                    file://node_modules/iconv-lite/LICENSE;md5=f942263d98f0d75e0e0101884e86261d \
                    file://node_modules/ieee754/LICENSE;md5=56c3be003027d64d24ca6b69a2612f2f \
                    file://node_modules/immediate/LICENSE.txt;md5=364b0dd2a53359063036a25106b75891 \
                    file://node_modules/inherits/LICENSE;md5=5b2ef2247af6d355ae9d9f988092d470 \
                    file://node_modules/ip-address/LICENSE;md5=b47884355df53dbe193454cb6a6e6b5b \
                    file://node_modules/is-stream/license;md5=d5f2a6dd0192dcc7c833e50bb9017337 \
                    file://node_modules/js-sdsl/LICENSE;md5=767604b1ed08213fd284407d38e9eec2 \
                    file://node_modules/js-yaml/LICENSE;md5=effd621a9bf5d72d6a7e6ef819bf3afb \
                    file://node_modules/json-schema-traverse/LICENSE;md5=ea87ade09b9e6da4f2e47904a4ee137b \
                    file://node_modules/json-stable-stringify-without-jsonify/LICENSE;md5=aea1cde69645f4b99be4ff7ca9abcce1 \
                    file://node_modules/jszip/LICENSE.markdown;md5=28c4d6943c892a3c61ac2aaa5d781861 \
                    file://node_modules/kuler/LICENSE;md5=07760fd1a905b41fefdc3763afb5dca5 \
                    file://node_modules/lie/license.md;md5=4397773a5104569a0a77b897f979a742 \
                    file://node_modules/logform/LICENSE;md5=69464058e29d977cf9b282e9d8a749e8 \
                    file://node_modules/lru-cache/LICENSE;md5=28b53f8938bb3cf7c37ed8ac5e7d233e \
                    file://node_modules/mime-db/LICENSE;md5=175b28b58359f8b4a969c9ab7c828445 \
                    file://node_modules/mime-types/LICENSE;md5=bf1f9ad1e2e1d507aef4883fff7103de \
                    file://node_modules/minimatch/LICENSE.md;md5=9f6dc78e3d8215fcb9da78e5ab9e29af \
                    file://node_modules/minimist/LICENSE;md5=aea1cde69645f4b99be4ff7ca9abcce1 \
                    file://node_modules/minipass/LICENSE.md;md5=95e9f67f2840df3a3a09a77ef3aea34b \
                    file://node_modules/mqtt-packet/LICENSE.md;md5=622e77e45833bb4f785aebc7e41c0bdb \
                    file://node_modules/mqtt/LICENSE.md;md5=261aa46f11e9a7bdbea1dea7eb8bcb6c \
                    file://node_modules/mqtt/node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/mqtt/node_modules/safe-buffer/LICENSE;md5=badd5e91c737e7ffdf10b40c1f907761 \
                    file://node_modules/mqtt/node_modules/string_decoder/LICENSE;md5=14af51f8c0a6c6e400b53e18c6e5f85c \
                    file://node_modules/ms/license.md;md5=2b8bc52ae6b7ba58e1629deabd53986f \
                    file://node_modules/multicast-dns/LICENSE;md5=a75272c6b584d0f8e2c1676b4e72469e \
                    file://node_modules/nan/LICENSE.md;md5=3952ff1c51e4ebe5b12c1bc501de4683 \
                    file://node_modules/node-addon-api/LICENSE.md;md5=fc3ff1120869be6b3cce17f9a06bfe2e \
                    file://node_modules/node-gyp-build/LICENSE;md5=bb7eae1c2fbb280c72665db9a1efc896 \
                    file://node_modules/number-allocator/LICENSE;md5=ceff88c0907db57b52716bb46eeaaa2a \
                    file://node_modules/on-finished/LICENSE;md5=1b1f7f9cec194121fdf616b971df7a7b \
                    file://node_modules/one-time/LICENSE;md5=4310a14e1d911cc6e4b5a34dbcbeaddd \
                    file://node_modules/package-json-from-dist/LICENSE.md;md5=add98d83942d5809bc719262ae40f4ce \
                    file://node_modules/pako/LICENSE;md5=a4f08d6b2d1bf3f3a1bc296a6109a25b \
                    file://node_modules/parseurl/LICENSE;md5=e7842ed4f188e53e53c3e8d9c4807e89 \
                    file://node_modules/path-scurry/LICENSE.md;md5=95e9f67f2840df3a3a09a77ef3aea34b \
                    file://node_modules/path-scurry/node_modules/lru-cache/LICENSE.md;md5=95e9f67f2840df3a3a09a77ef3aea34b \
                    file://node_modules/process-nextick-args/license.md;md5=216769dac98a78ec088ee7cc6fad1dfa \
                    file://node_modules/process/LICENSE;md5=460a1c62fb575fc77668890ec8d03d0b \
                    file://node_modules/range-parser/LICENSE;md5=d4246fb961a4f121eef5ffca47f0b010 \
                    file://node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/require-from-string/license;md5=be72c3ad86c1c4e9578a1945b082b17d \
                    file://node_modules/rfdc/LICENSE;md5=fc2ea1f4c58a804909742c8eadede5ea \
                    file://node_modules/rimraf/LICENSE.md;md5=95e9f67f2840df3a3a09a77ef3aea34b \
                    file://node_modules/safe-buffer/LICENSE;md5=badd5e91c737e7ffdf10b40c1f907761 \
                    file://node_modules/safe-stable-stringify/LICENSE;md5=33bb86b5139085dce121f21193b6130c \
                    file://node_modules/safer-buffer/LICENSE;md5=3baebc2a17b8f5bff04882cd0dc0f76e \
                    file://node_modules/semver/LICENSE;md5=82703a69f6d7411dde679954c2fd9dca \
                    file://node_modules/send/LICENSE;md5=5f1a8369a899b128aaa8a59d60d00b40 \
                    file://node_modules/serve-static/LICENSE;md5=27b1707520b14d0bc890f4e75cd387b0 \
                    file://node_modules/setimmediate/LICENSE.txt;md5=af19eaafbc6aa0d2c97538dcfb6db94a \
                    file://node_modules/setprototypeof/LICENSE;md5=4846f1626304c2c0f806a539bbc7d54a \
                    file://node_modules/slip/GPL-LICENSE.txt;md5=2c1778696d3ba68569a0352e709ae6b7 \
                    file://node_modules/slip/MIT-LICENSE.txt;md5=b136108764931d4b8b8aacf3ea1af2ff \
                    file://node_modules/smart-buffer/LICENSE;md5=5b37b090a43e81bd880398260c467866 \
                    file://node_modules/socks/LICENSE;md5=742dc14598fb295b01df682683c57709 \
                    file://node_modules/source-map-support/LICENSE.md;md5=f433e270f6b1d088c38b279d53048f5e \
                    file://node_modules/source-map/LICENSE;md5=b1ca6dbc0075d56cbd9931a75566cd44 \
                    file://node_modules/split2/LICENSE;md5=a3b84061387696c9678867c878a6bbc3 \
                    file://node_modules/stack-trace/License;md5=9fbf93f7a763e64c0a30207b5693bf75 \
                    file://node_modules/statuses/LICENSE;md5=36e2bc837ce69a98cc33a9e140d457e5 \
                    file://node_modules/string_decoder/LICENSE;md5=14af51f8c0a6c6e400b53e18c6e5f85c \
                    file://node_modules/text-hex/LICENSE;md5=d699750183031d92d152d4fb1270b705 \
                    file://node_modules/throttleit/license;md5=a0dcfe5aa5c37ab9c98899d7eb6ea46c \
                    file://node_modules/thunky/LICENSE;md5=0033175ba371b569c73d23fd726c37e8 \
                    file://node_modules/toidentifier/LICENSE;md5=1a261071a044d02eb6f2bb47f51a3502 \
                    file://node_modules/triple-beam/LICENSE;md5=6662ccc5390189c4606fb6b2f726360b \
                    file://node_modules/tslib/CopyrightNotice.txt;md5=cb391e9e435b114c07bee8f6754c4f98 \
                    file://node_modules/tslib/LICENSE.txt;md5=f938d99cba29007eeae26d80a9a4cfa6 \
                    file://node_modules/typedarray/LICENSE;md5=6085b70b74c7dcf7df4e955725e3153d \
                    file://node_modules/undici-types/LICENSE;md5=5809878683f4d0ea6781d9d18e6e6baa \
                    file://node_modules/unix-dgram/LICENSE;md5=1446bae1883758d95cb9a38560963fe9 \
                    file://node_modules/util-deprecate/LICENSE;md5=b7c99ef4b0f3ad9911a52219947f8cf0 \
                    file://node_modules/winston-syslog/LICENSE;md5=207f20dc9776b79d0aea3637bb06dc03 \
                    file://node_modules/winston-syslog/node_modules/unix-dgram/LICENSE;md5=1446bae1883758d95cb9a38560963fe9 \
                    file://node_modules/winston-transport/LICENSE;md5=05a0729dc3291972c7b9e521a99a3c1f \
                    file://node_modules/winston-transport/node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/winston/LICENSE;md5=124783bb03d1b801c23d11f07b62be0a \
                    file://node_modules/winston/node_modules/readable-stream/LICENSE;md5=a67a7926e54316d90c14f74f71080977 \
                    file://node_modules/worker-factory/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/worker-timers-broker/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/worker-timers-worker/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/worker-timers/LICENSE;md5=549539a42815438eb362ca5d16e45114 \
                    file://node_modules/ws/LICENSE;md5=7a4bd929a6c0e6951846d75e53fc9f51 \
                    file://node_modules/zigbee-herdsman-converters/LICENSE;md5=d1455712aeab4e6c11dbca72e1c0ec27 \
                    file://node_modules/zigbee-herdsman/LICENSE;md5=af7b83a5ff1162a26feb1a736e65b1f5 \
                    file://node_modules/zigbee-on-host/LICENSE;md5=1ebbd3e34237af26da5dc08a4e440464 \
                    file://node_modules/zigbee2mqtt-frontend/LICENSE;md5=1ebbd3e34237af26da5dc08a4e440464 \
                    file://node_modules/zigbee2mqtt-windfront/LICENSE;md5=1ebbd3e34237af26da5dc08a4e440464 \
                    file://node_modules/isarray/README.md;md5=e7424a48d45a2e04d52c15e786681063 \
                    file://node_modules/object-assign-deep/README.md;md5=57029acacb655ec3d591d698fe251171"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    npm://registry.npmjs.org/;package=zigbee2mqtt;version=${PV} \
    npmsw://${THISDIR}/files/npm-shrinkwrap.json \
    file://services/zigbee2mqtt.service \
    file://99-zigbee.rules \
    file://configuration.yaml \
    "

S = "${WORKDIR}/npm"

inherit npm systemd

SYSTEMD_SERVICE:${PN} = "zigbee2mqtt.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

FILES:${PN} += " \
    ${sysconfdir}/udev/rules.d/99-zigbee.rules \
    /var/lib/zigbee2mqtt \
"

# @serialport/bindings-cpp ships pre-built native .node binaries for multiple
# architectures/libcs. These arrive already stripped; suppress the QA error
# and remove prebuilds that don't match the target to save space.
INSANE_SKIP:${PN} += "already-stripped"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/zigbee2mqtt.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${WORKDIR}/99-zigbee.rules ${D}${sysconfdir}/udev/rules.d/

    install -d ${D}/var/lib/zigbee2mqtt
    install -m 0644 ${WORKDIR}/configuration.yaml ${D}/var/lib/zigbee2mqtt/

    prebuild_dir="${D}${prefix}/lib/node_modules/zigbee2mqtt/node_modules/@serialport/bindings-cpp/prebuilds"
    if [ -d "${prebuild_dir}" ]; then
        case "${TARGET_ARCH}" in
            aarch64) keep="linux-arm64" ;;
            x86_64)  keep="linux-x64"  ;;
            arm*)    keep="linux-arm"  ;;
            *)       keep=""           ;;
        esac
        if [ -n "${keep}" ]; then
            # Remove prebuilds for other platforms.
            for dir in "${prebuild_dir}"/*/; do
                [ "$(basename "${dir}")" != "${keep}" ] && rm -rf "${dir}"
            done
            # Within the kept platform dir, remove musl binaries — target uses glibc.
            find "${prebuild_dir}/${keep}" -name "*.musl.node" -delete
        fi
    fi
}

LICENSE:${PN} = "GPL-3.0-only"
LICENSE:${PN}-babel-runtime = "MIT"
LICENSE:${PN}-colors-colors = "Unknown"
LICENSE:${PN}-dabh-diagnostics = "MIT"
LICENSE:${PN}-date-fns-tz = "MIT"
LICENSE:${PN}-leichtgewicht-ip-codec = "MIT"
LICENSE:${PN}-serialport-bindings-cpp = "MIT"
LICENSE:${PN}-serialport-bindings-cpp-debug = "Unknown"
LICENSE:${PN}-serialport-bindings-interface = "MIT"
LICENSE:${PN}-serialport-parser-delimiter = "MIT"
LICENSE:${PN}-serialport-parser-readline = "MIT"
LICENSE:${PN}-serialport-stream = "MIT"
LICENSE:${PN}-serialport-stream-debug = "Unknown"
LICENSE:${PN}-so-ric-colorspace = "MIT"
LICENSE:${PN}-types-node = "MIT"
LICENSE:${PN}-types-readable-stream = "MIT"
LICENSE:${PN}-types-triple-beam = "MIT"
LICENSE:${PN}-types-ws = "MIT"
LICENSE:${PN}-abort-controller = "MIT"
LICENSE:${PN}-ajv = "MIT"
LICENSE:${PN}-argparse = "Unknown"
LICENSE:${PN}-async = "MIT"
LICENSE:${PN}-balanced-match = "MIT"
LICENSE:${PN}-base64-js = "MIT"
LICENSE:${PN}-bind-decorator = "MIT"
LICENSE:${PN}-bindings = "MIT"
LICENSE:${PN}-bl = "MIT & Unknown"
LICENSE:${PN}-bl-readable-stream = "Unknown"
LICENSE:${PN}-bl-safe-buffer = "Unknown"
LICENSE:${PN}-bl-stringdecoder = "Unknown"
LICENSE:${PN}-bonjour-service = "Unknown"
LICENSE:${PN}-brace-expansion = "MIT"
LICENSE:${PN}-broker-factory = "MIT"
LICENSE:${PN}-buffer = "MIT"
LICENSE:${PN}-buffer-from = "MIT"
LICENSE:${PN}-color = "MIT"
LICENSE:${PN}-color-convert = "MIT"
LICENSE:${PN}-color-name = "MIT"
LICENSE:${PN}-color-string = "MIT"
LICENSE:${PN}-commist = "MIT"
LICENSE:${PN}-concat-stream = "MIT & Unknown"
LICENSE:${PN}-concat-stream-readable-stream = "Unknown"
LICENSE:${PN}-core-util-is = "MIT"
LICENSE:${PN}-debounce = "Unknown"
LICENSE:${PN}-debug = "MIT"
LICENSE:${PN}-depd = "MIT"
LICENSE:${PN}-dns-packet = "MIT"
LICENSE:${PN}-ee-first = "MIT"
LICENSE:${PN}-enabled = "MIT"
LICENSE:${PN}-encodeurl = "MIT"
LICENSE:${PN}-escape-html = "MIT"
LICENSE:${PN}-etag = "MIT"
LICENSE:${PN}-event-target-shim = "MIT"
LICENSE:${PN}-events = "MIT"
LICENSE:${PN}-express-static-gzip = "MIT"
LICENSE:${PN}-fast-deep-equal = "MIT"
LICENSE:${PN}-fast-unique-numbers = "MIT"
LICENSE:${PN}-fast-uri = "Unknown"
LICENSE:${PN}-fecha = "MIT"
LICENSE:${PN}-file-uri-to-path = "MIT"
LICENSE:${PN}-finalhandler = "MIT"
LICENSE:${PN}-fnname = "MIT"
LICENSE:${PN}-fresh = "MIT"
LICENSE:${PN}-glob = "Unknown"
LICENSE:${PN}-glossy = "MIT"
LICENSE:${PN}-help-me = "MIT"
LICENSE:${PN}-http-errors = "MIT"
LICENSE:${PN}-humanize-duration = "Unknown"
LICENSE:${PN}-iconv-lite = "MIT"
LICENSE:${PN}-ieee754 = "BSD-3-Clause"
LICENSE:${PN}-immediate = "MIT"
LICENSE:${PN}-inherits = "ISC"
LICENSE:${PN}-ip-address = "MIT"
LICENSE:${PN}-is-stream = "MIT"
LICENSE:${PN}-isarray = "Unknown"
LICENSE:${PN}-js-sdsl = "MIT"
LICENSE:${PN}-js-yaml = "MIT"
LICENSE:${PN}-json-schema-traverse = "MIT"
LICENSE:${PN}-json-stable-stringify-without-jsonify = "MIT"
LICENSE:${PN}-jszip = "Unknown"
LICENSE:${PN}-kuler = "MIT"
LICENSE:${PN}-lie = "MIT"
LICENSE:${PN}-logform = "MIT"
LICENSE:${PN}-lru-cache = "ISC"
LICENSE:${PN}-mime-db = "MIT"
LICENSE:${PN}-mime-types = "MIT"
LICENSE:${PN}-minimatch = "Unknown"
LICENSE:${PN}-minimist = "MIT"
LICENSE:${PN}-minipass = "BlueOak-1.0.0"
LICENSE:${PN}-mqtt = "MIT & Unknown"
LICENSE:${PN}-mqtt-packet = "Unknown"
LICENSE:${PN}-mqtt-readable-stream = "Unknown"
LICENSE:${PN}-mqtt-safe-buffer = "Unknown"
LICENSE:${PN}-mqtt-stringdecoder = "Unknown"
LICENSE:${PN}-ms = "MIT"
LICENSE:${PN}-multicast-dns = "MIT"
LICENSE:${PN}-nan = "MIT"
LICENSE:${PN}-node-addon-api = "MIT"
LICENSE:${PN}-node-gyp-build = "MIT"
LICENSE:${PN}-number-allocator = "MIT"
LICENSE:${PN}-object-assign-deep = "Unknown"
LICENSE:${PN}-on-finished = "MIT"
LICENSE:${PN}-one-time = "MIT"
LICENSE:${PN}-package-json-from-dist = "Unknown"
LICENSE:${PN}-pako = "MIT"
LICENSE:${PN}-parseurl = "MIT"
LICENSE:${PN}-path-scurry = "BlueOak-1.0.0"
LICENSE:${PN}-path-scurry-lru-cache = "Unknown"
LICENSE:${PN}-process = "MIT"
LICENSE:${PN}-process-nextick-args = "MIT"
LICENSE:${PN}-range-parser = "MIT"
LICENSE:${PN}-readable-stream = "Unknown"
LICENSE:${PN}-require-from-string = "MIT"
LICENSE:${PN}-rfdc = "MIT"
LICENSE:${PN}-rimraf = "BlueOak-1.0.0"
LICENSE:${PN}-safe-buffer = "MIT"
LICENSE:${PN}-safe-stable-stringify = "MIT"
LICENSE:${PN}-safer-buffer = "MIT"
LICENSE:${PN}-semver = "ISC"
LICENSE:${PN}-send = "MIT"
LICENSE:${PN}-serve-static = "MIT"
LICENSE:${PN}-setimmediate = "MIT"
LICENSE:${PN}-setprototypeof = "ISC"
LICENSE:${PN}-slip = "MIT & Unknown"
LICENSE:${PN}-smart-buffer = "MIT"
LICENSE:${PN}-socks = "MIT"
LICENSE:${PN}-source-map = "Unknown"
LICENSE:${PN}-source-map-support = "MIT"
LICENSE:${PN}-split2 = "ISC"
LICENSE:${PN}-stack-trace = "MIT"
LICENSE:${PN}-statuses = "MIT"
LICENSE:${PN}-stringdecoder = "Unknown"
LICENSE:${PN}-text-hex = "MIT"
LICENSE:${PN}-throttleit = "MIT"
LICENSE:${PN}-thunky = "MIT"
LICENSE:${PN}-toidentifier = "MIT"
LICENSE:${PN}-triple-beam = "MIT"
LICENSE:${PN}-tslib = "0BSD"
LICENSE:${PN}-typedarray = "Unknown"
LICENSE:${PN}-undici-types = "MIT"
LICENSE:${PN}-unix-dgram = "ISC"
LICENSE:${PN}-util-deprecate = "MIT"
LICENSE:${PN}-winston = "MIT & Unknown"
LICENSE:${PN}-winston-syslog = "ISC & MIT"
LICENSE:${PN}-winston-syslog-unix-dgram = "Unknown"
LICENSE:${PN}-winston-transport = "MIT & Unknown"
LICENSE:${PN}-winston-transport-readable-stream = "Unknown"
LICENSE:${PN}-winston-readable-stream = "Unknown"
LICENSE:${PN}-worker-factory = "MIT"
LICENSE:${PN}-worker-timers = "MIT"
LICENSE:${PN}-worker-timers-broker = "MIT"
LICENSE:${PN}-worker-timers-worker = "MIT"
LICENSE:${PN}-ws = "MIT"
LICENSE:${PN}-zigbee-herdsman = "MIT"
LICENSE:${PN}-zigbee-herdsman-converters = "MIT"
LICENSE:${PN}-zigbee-on-host = "Unknown"
LICENSE:${PN}-zigbee2mqtt-frontend = "Unknown"
LICENSE:${PN}-zigbee2mqtt-windfront = "Unknown"
