package com.jiezhirbmkmod;

/**
 * Single source of the mod identity.
 *
 * <p>The mod id/name/version used to be duplicated between the
 * {@code @Mod} annotation, {@code RbmkMonitorMod.VERSION}, build.gradle and
 * mcmod.info. They now live here only; {@code @Mod} references these
 * compile-time constants, and the Gradle {@code verifyModMetadata} task fails
 * the build when {@link #VERSION} and {@code project.version} disagree.
 * mcmod.info carries a {@code @VERSION@} token that Gradle replaces at build
 * time, so the packaged metadata can never lag behind this file.</p>
 *
 * <p>Keep the string literals ASCII-safe: {@link #NAME} uses unicode escapes
 * for the Chinese characters, matching the rest of the source tree.</p>
 */
public final class Tags {

    public static final String MODID = "jiezhirbmkmod";
    public static final String NAME = "RBMK\u663e\u793a\u5c4f";
    public static final String VERSION = "18.3";

    private Tags() {
    }
}
