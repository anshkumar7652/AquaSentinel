package com.aquasentinel.app;

/**
 * Entry point wrapper for JavaFX application execution in fat JARs / native bundles.
 * This ensures JavaFX runtime libraries are properly initialized without triggering
 * the JavaFX runtime component missing error when launched directly from a standalone JAR or native exe.
 */
public class AppLauncher {
    public static void main(String[] args) {
        AquaSentinelApp.main(args);
    }
}
