package com.factcheck.collector.processor;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class BaseProcessor<T> {

    public final List<T> process() {
        var raw = fetchData();
        var valid = validate(raw);
        var mapped = map(valid);
        return filterExisting(mapped);
    }

    protected abstract List<T> fetchData();
    protected abstract List<T> validate(List<T> data);
    protected abstract List<T> map(List<T> data);
    protected abstract List<T> filterExisting(List<T> data);

    protected boolean validateField(String object, String field, String value) {
        if (value != null && !value.isBlank()) return true;
        log.warn("Skipping item: missing {} -> {}", field, object);
        return false;
    }
}