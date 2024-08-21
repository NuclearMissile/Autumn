package org.example.scan

import org.example.autumn.annotation.Import
import org.example.imported.LocalDateConfiguration
import org.example.imported.ZonedDateConfiguration

@Import(LocalDateConfiguration::class, ZonedDateConfiguration::class)
class ScanConfiguration
