package com.nazir.ecommerce.productservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration.
 * <p>
 * @EnableMongoAuditing:
 * Required for @CreatedDate and @LastModifiedDate to be automatically
 * populated by Spring Data MongoDB.
 * Without this annotation those fields will always be null.
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.nazir.ecommerce.productservice.repository")
public class MongoConfig {
}
