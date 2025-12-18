package com.factcheck.collector.aggregator;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.source.DataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class NewsAggregator extends BaseAggregator<NewsArticleDto> {
    public NewsAggregator(List<DataSource<NewsArticleDto>> sources, Executor ingestionExecutor) {
        super(sources, ingestionExecutor);
    }
}
