package no.uio.bedreflyt.api

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ComponentScan(basePackages = ["no.uio.bedreflyt.api.config"])
@EnableJpaRepositories(basePackages = ["no.uio.bedreflyt.api.repository.live"])
open class API

fun main(args: Array<String>) {
    SpringApplication.run(API::class.java, *args)
}