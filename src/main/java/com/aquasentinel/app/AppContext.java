package com.aquasentinel.app;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Immutable application configuration and runtime paths context.
 */
public record AppContext(Path dataDirectory, Path databasePath, boolean demoMode) {

    public static AppContext fromArgs(String[] args) {
        String dataDirOverride = null;
        boolean demo = false;

        for (int i = 0; i < args.length; i++) {
            if ("--data-dir".equals(args[i]) && i + 1 < args.length) {
                dataDirOverride = args[++i];
            } else if ("--demo".equals(args[i])) {
                demo = true;
            }
        }

        Path baseDir;
        if (dataDirOverride != null && !dataDirOverride.isBlank()) {
            baseDir = Paths.get(dataDirOverride);
        } else {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                baseDir = Paths.get(localAppData, "AquaSentinel");
            } else {
                baseDir = Paths.get(System.getProperty("user.home"), ".aquasentinel");
            }
        }

        Path dbPath = baseDir.resolve("aquasentinel.db");
        return new AppContext(baseDir, dbPath, demo);
    }
}
