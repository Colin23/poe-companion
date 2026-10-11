package com.colinmoerbe.poecompanion.acquisition

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(displayName = "Acquisition", allowedDependencies = ["catalog", "league"])
@PackageInfo
internal class ModuleMetadata
