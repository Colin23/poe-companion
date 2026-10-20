package com.colinmoerbe.poecompanion.league

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(displayName = "League", allowedDependencies = ["security :: identity"])
@PackageInfo
internal class ModuleMetadata
