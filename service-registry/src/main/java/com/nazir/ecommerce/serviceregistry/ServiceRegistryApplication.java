package com.nazir.ecommerce.serviceregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Registry — Netflix Eureka Server.
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * What is Service Discovery?
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * Problem without it:
 * order-service needs to call product-service.
 * Hardcode: http://192.168.1.50:8085  ← breaks if IP changes
 * Hardcode: http://product-service:8085  ← only works in Docker
 * In Kubernetes: pods get random IPs, restart constantly
 * <p>
 * Solution — Service Registry:
 * 1. product-service starts → registers itself:
 * { name: "product-service", host: "10.0.1.50", port: 8085 }
 * <p>
 * 2. order-service wants to call product-service:
 * → Asks Eureka: "Where is product-service?"
 * → Eureka returns: [{ host: "10.0.1.50", port: 8085 }]
 * → order-service calls http://10.0.1.50:8085/api/v1/products/123
 * <p>
 * 3. product-service scales to 3 instances:
 * → Eureka returns 3 addresses
 * → Spring Cloud LoadBalancer picks one (round-robin)
 * → Zero config change needed in order-service
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * Heartbeat mechanism
 * ══════════════════════════════════════════════════════════════════════
 * Every registered service sends a heartbeat every 30 seconds.
 * If Eureka doesn't hear from a service for 90 seconds → evicts it.
 * This ensures stale/crashed instances are removed automatically.
 * <p>
 * ══════════════════════════════════════════════════════════════════════
 * Self-preservation mode
 * ══════════════════════════════════════════════════════════════════════
 * If Eureka receives fewer heartbeats than expected (network partition?),
 * it enters self-preservation mode: STOPS evicting instances.
 * Reason: "If I can't hear from 50% of my services, maybe MY network
 * is broken, not the services — don't evict them."
 * We disable this in dev (renewalPercentageThreshold: 0.49)
 * so crashed services get evicted quickly during testing.
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
