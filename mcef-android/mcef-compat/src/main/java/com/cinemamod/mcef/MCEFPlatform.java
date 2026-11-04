package com.cinemamod.mcef;

import java.util.Locale;

/** 只有 ANDROID_ARM64：x86_64 模拟器走同一份代码，Cefrium 的 AAR 里也带了。 */
public enum MCEFPlatform {
    ANDROID_ARM64("android-arm64"),
    ANDROID_X86_64("android-x86_64");

    private final String normalized;

    MCEFPlatform(String normalized) { this.normalized = normalized; }

    public String getNormalizedName() { return normalized.toLowerCase(Locale.US); }

    public static MCEFPlatform getPlatform() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.US);
        if (arch.equals("aarch64") || arch.equals("arm64")) return ANDROID_ARM64;
        if (arch.equals("x86_64") || arch.equals("amd64")) return ANDROID_X86_64;
        throw new RuntimeException("Unsupported platform: " + arch);
    }
}
