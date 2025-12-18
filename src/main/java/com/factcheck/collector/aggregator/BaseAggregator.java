package com.factcheck.collector.aggregator;

import com.factcheck.collector.source.DataSource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseAggregator<T> {

    protected final List<DataSource<T>> sources;
    private final Executor executor;

    protected BaseAggregator(List<DataSource<T>> sources, Executor executor) {
        this.sources = sources;
        this.executor = executor;
    }

    public List<T> aggregateAsync() {
        List<CompletableFuture<List<T>>> futures = sources.stream()
                .map(src -> CompletableFuture.supplyAsync(() -> {
                    List<T> data = src.fetchData();
                    log.info("Fetched {} entries from {}", data.size(), src.getClass().getSimpleName());
                    return data;
                }, executor).exceptionally(ex -> {
                    log.warn("Source {} failed: {}", src.getClass().getSimpleName(), ex.toString());
                    return List.of();
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
