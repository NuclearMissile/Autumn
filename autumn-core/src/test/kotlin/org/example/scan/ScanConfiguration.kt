package org.example.scan

import org.example.autumn.annotation.ComponentScan
import org.example.autumn.annotation.Import
import org.example.imported.LocalDateConfiguration
import org.example.imported.ZonedDateConfiguration

@ComponentScan
@Import(LocalDateConfiguration::class, ZonedDateConfiguration::class)
class ScanConfiguration
