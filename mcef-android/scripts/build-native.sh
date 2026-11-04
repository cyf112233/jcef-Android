#!/usr/bin/env bash
# 编 libmcef.so 并放进插件 APK 的 jniLibs。
set -euo pipefail

: "${ANDROID_NDK:?需要先 export ANDROID_NDK=/path/to/ndk}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ABI="${1:-arm64-v8a}"

cmake -S "$ROOT/mcef-native" -B "$ROOT/mcef-native/build-$ABI" \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM=android-29 \
  -DCMAKE_BUILD_TYPE=Release \
  -G Ninja

ninja -C "$ROOT/mcef-native/build-$ABI"

mkdir -p "$ROOT/cefhost/src/main/jniLibs/$ABI"
cp "$ROOT/mcef-native/build-$ABI/libmcef.so" "$ROOT/cefhost/src/main/jniLibs/$ABI/"
"$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" \
  "$ROOT/cefhost/src/main/jniLibs/$ABI/libmcef.so" || true

echo "已就绪: cefhost/src/main/jniLibs/$ABI/libmcef.so"
