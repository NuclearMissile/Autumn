package org.example.autumn.hello;

import org.example.autumn.annotation.ComponentScan;
import org.example.autumn.annotation.Configuration;
import org.example.autumn.annotation.Import;
import org.example.autumn.aop.AroundAopConfiguration;
import org.example.autumn.db.DbConfiguration;
import org.example.autumn.eventbus.EventBusConfig;
import org.example.autumn.servlet.WebMvcConfiguration;

@ComponentScan
@Configuration
@Import({WebMvcConfiguration.class, DbConfiguration.class, AroundAopConfiguration.class, EventBusConfig.class})
class HelloConfiguration {
}
