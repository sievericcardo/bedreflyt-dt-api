package no.uio.bedreflyt.api.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["no.uio.bedreflyt.api"])
open class AppConfig