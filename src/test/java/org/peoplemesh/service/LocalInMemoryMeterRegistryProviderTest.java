package org.peoplemesh.service;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalInMemoryMeterRegistryProviderTest {

    private final LocalInMemoryMeterRegistryProvider provider = new LocalInMemoryMeterRegistryProvider();

    @Test
    void attachLocalInMemoryRegistry_addsSimpleRegistryOnce() {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();

        provider.attachLocalInMemoryRegistry(null, composite);
        provider.attachLocalInMemoryRegistry(null, composite);

        long simpleCount = composite.getRegistries().stream()
                .filter(SimpleMeterRegistry.class::isInstance)
                .count();
        assertEquals(1L, simpleCount);
    }

    @Test
    void attachLocalInMemoryRegistry_noopForNonCompositeRegistry() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        provider.attachLocalInMemoryRegistry(null, simple);
        assertEquals(0, simple.getMeters().size());
    }
}
