package com.example.scan

import com.example.autumn.annotation.ComponentScan
import com.example.autumn.annotation.Import
import com.example.imported.LocalDateConfiguration
import com.example.imported.ZonedDateConfiguration

@ComponentScan
@Import(LocalDateConfiguration::class, ZonedDateConfiguration::class)
class ScanApplication
