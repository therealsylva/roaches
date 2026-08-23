#!/usr/bin/env bash
set -euo pipefail

tools_dir="${TOOLS_DIR:-.tools}"
apktool_version="3.0.3"
jadx_version="1.5.6"
apktool_sha256="dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423"
jadx_sha256="545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974"

mkdir -p "${tools_dir}"

download_verified() {
  local url="$1"
  local destination="$2"
  local expected_sha256="$3"

  if [[ -f "${destination}" ]] &&
    [[ "$(sha256sum "${destination}" | awk '{print $1}')" == "${expected_sha256}" ]]; then
    return
  fi

  curl --fail --location --retry 3 --output "${destination}.tmp" "${url}"
  printf '%s  %s\n' "${expected_sha256}" "${destination}.tmp" | sha256sum --check -
  mv "${destination}.tmp" "${destination}"
}

download_verified \
  "https://github.com/iBotPeaches/Apktool/releases/download/v${apktool_version}/apktool_${apktool_version}.jar" \
  "${tools_dir}/apktool.jar" \
  "${apktool_sha256}"

download_verified \
  "https://github.com/skylot/jadx/releases/download/v${jadx_version}/jadx-${jadx_version}.zip" \
  "${tools_dir}/jadx.zip" \
  "${jadx_sha256}"

if [[ ! -x "${tools_dir}/jadx/bin/jadx" ]]; then
  mkdir -p "${tools_dir}/jadx"
  unzip -oq "${tools_dir}/jadx.zip" -d "${tools_dir}/jadx"
fi

java -jar "${tools_dir}/apktool.jar" --version
"${tools_dir}/jadx/bin/jadx" --version
