package com.factcheck.collector.source;

import java.util.List;

public interface DataSource<T> {
    List<T> fetchData();
}
