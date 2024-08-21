package org.example.autumn.hello;

import org.example.autumn.annotation.Import;
import org.example.autumn.aop.AroundConfiguration;
import org.example.autumn.db.DbConfiguration;
import org.example.autumn.eventbus.EventBusConfiguration;
import org.example.autumn.servlet.WebMvcConfiguration;

@Import({WebMvcConfiguration.class, DbConfiguration.class, AroundConfiguration.class, EventBusConfiguration.class})
class HelloConfig {
}
