package org.peoplemesh.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class LocalInMemoryMeterRegistryProvider {

    void attachLocalInMemoryRegistry(@Observes StartupEvent ignored, MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry composite) {
            boolean alreadyPresent = composite.getRegistries().stream()
                    .anyMatch(SimpleMeterRegistry.class::isInstance);
            if (!alreadyPresent) {
                composite.add(new SimpleMeterRegistry());
            }
        }
    }
}
