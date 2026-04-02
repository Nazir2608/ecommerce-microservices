package com.nazir.ecommerce.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based deduplication — prevents duplicate emails.
 *
 * LEARNING POINT — setIfAbsent (Redis SETNX):
 *   Atomically sets key=value only if key does NOT already exist.
 *   Returns true  → key was set   → event is new, process it
 *   Returns false → key existed   → event already processed, skip it
 *
 *   This is atomic at the Redis level — thread-safe even with
 *   multiple notification-service instances running simultaneously.
 *
 * LEARNING POINT — TTL of 24 hours:
 *   Keys expire after 24h to prevent Redis growing forever.
 *   If the same event arrives after 24h (extremely unlikely) it would
 *   be processed again — acceptable trade-off for simplicity.
 *   For stricter guarantees: use a longer TTL or a persistent store.
 *
 * Key format: "notif:dedup:{eventId}"
 *   e.g. "notif:dedup:3f2a1b4c-9d8e-..."
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final StringRedisTemplate redis;

    private static final Duration TTL     = Duration.ofHours(24);
    private static final String   PREFIX  = "notif:dedup:";

    /**
     * @return true  if this is the FIRST time we see this eventId → process it
     *         false if we've seen this eventId before → skip (duplicate)
     */
    public boolean isNew(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Received event with null/blank eventId — allowing through");
            return true;
        }
        String key = PREFIX + eventId;
        Boolean wasSet = redis.opsForValue().setIfAbsent(key, "1", TTL);
        boolean isNew = Boolean.TRUE.equals(wasSet);
        if (!isNew) {
            log.debug("Duplicate event detected, skipping: eventId={}", eventId);
        }
        return isNew;
    }
}
