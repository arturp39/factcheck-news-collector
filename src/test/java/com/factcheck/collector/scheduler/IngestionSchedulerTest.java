package com.factcheck.collector.scheduler;

import com.factcheck.collector.service.SourceIngestionService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IngestionSchedulerTest {

    @Test
    void run_callsServiceWithRandomCorrelationId() {
        SourceIngestionService svc = mock(SourceIngestionService.class);
        IngestionScheduler scheduler = new IngestionScheduler(svc);

        scheduler.run();

        var cap = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(svc).ingestOnce(cap.capture());
        assertThat(cap.getValue()).isNotNull();
    }
}