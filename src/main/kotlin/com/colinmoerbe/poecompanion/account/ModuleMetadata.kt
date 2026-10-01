package com.colinmoerbe.poecompanion.account

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(displayName = "Account", allowedDependencies = ["catalog", "league"])
@PackageInfo
internal class ModuleMetadata
