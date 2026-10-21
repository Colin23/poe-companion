package com.colinmoerbe.poecompanion.build

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(displayName = "Build", allowedDependencies = ["account", "catalog", "league", "security :: authorization"])
@PackageInfo
internal class ModuleMetadata
