package com.colinmoerbe.poecompanion.browser

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Server-rendered incoming adapter; depends only on public application-module APIs.
 * Controllers, view composition and presentation DTOs remain Browser-internal.
 */
@ApplicationModule(
    displayName = "Browser",
    allowedDependencies = [
        "account",
        "acquisition",
        "acquisition :: sourceidentity",
        "acquisition :: encounter",
        "acquisition :: knowledge",
        "acquisition :: relationship",
        "acquisition :: probability",
        "acquisition :: attempts",
        "build",
        "catalog",
        "league",
    ],
)
@PackageInfo
internal class ModuleMetadata
