package com.colinmoerbe.poecompanion.acquisition

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(displayName = "Acquisition", allowedDependencies = ["catalog", "league", "security :: authorization"])
@PackageInfo
internal class ModuleMetadata
