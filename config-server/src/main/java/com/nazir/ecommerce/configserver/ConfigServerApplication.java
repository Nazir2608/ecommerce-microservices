package com.nazir.ecommerce.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server — centralised configuration for all microservices.
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * Why centralised config?
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * Without Config Server:
 * Each service has its own application.yml baked into its Docker image.
 * Change a DB password → rebuild + redeploy ALL services.
 * Different configs per environment (dev/staging/prod) → 5 services × 3 envs
 * = 15 config files to keep in sync.
 * <p>
 * With Config Server:
 * All config lives in ONE place (here, backed by classpath or Git).
 * Services fetch their config on startup:
 * GET http://config-server:8888/user-service/docker
 * → returns user-service.yml + application.yml merged
 * Change a DB password → update ONE file, restart ONE service.
 * Environment-specific config: user-service-docker.yml overrides user-service.yml
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * Config resolution order (Spring Cloud Config)
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * Highest priority (wins in conflicts):
 * 1. {appName}-{profile}.yml   e.g. user-service-docker.yml
 * 2. {appName}.yml             e.g. user-service.yml
 * 3. application-{profile}.yml e.g. application-docker.yml
 * 4. application.yml           (shared defaults for ALL services)
 * <p>
 * Example: user-service running with profile "docker"
 * Reads: application.yml        → base defaults (kafka, zipkin, etc.)
 * Reads: user-service.yml       → user-service specifics
 * Reads: user-service-docker.yml → docker overrides (DB host = postgres:5432)
 * Result: merged, docker-specific values win
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * LNative vs Git backend
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * Native (this project): reads config from classpath:/configs/
 * Pros:  simple, no Git setup needed, works offline
 * Cons:  changing config requires rebuilding the config-server image
 * <p>
 * Git backend (production recommended):
 * spring.cloud.config.server.git.uri: https://github.com/org/ecommerce-config
 * Pros:  config changes = Git commit (audit trail, PR reviews, rollback)
 * zero rebuild needed — config-server pulls latest on refresh
 * Cons:  needs Git credentials, network access to Git host
 * <p>
 * To switch to Git: change application.yml and add Git URI + credentials.
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
