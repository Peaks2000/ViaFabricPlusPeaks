#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
    echo "usage: $0 [latest.log]" >&2
    exit 2
fi

log_file="${1:-}"
if [[ -z "$log_file" ]]; then
    prism_instances="$HOME/.local/share/PrismLauncher/instances"
    if [[ -d "$prism_instances" ]]; then
        newest_record="$(find "$prism_instances" -type f -path '*/minecraft/logs/latest.log' -printf '%T@ %p\n' | sort -nr | head -n 1 || true)"
        log_file="${newest_record#* }"
    fi
fi

if [[ -z "$log_file" || ! -f "$log_file" ]]; then
    echo "No PrismLauncher latest.log found; pass an explicit log path." >&2
    exit 1
fi

echo "Bedrock diagnostic log: $log_file"
rg --line-number --context 5 --ignore-case \
    '^\[[0-9]{2}:[0-9]{2}:[0-9]{2}\].*(Bedrock|NetherNet|WebRTC|RakNet|Xbox|MPSD|PlayStatus|RESOURCE_PACK|decoder exception|IndexOutOfBounds|failed to join|SIGNAL_CONNECT_ERROR|Remote peer sent connect error|ServerIdConflict|NotAuthenticated|ExceptionInInitializerError|NoClassDefFoundError)|Packet Type:|Caused by:.*(IndexOutOfBounds|DecoderException|Load library|NullPointerException)|at .*(viabedrock|PeerConnectionFactory|NativeLoader)' \
    "$log_file" || true
