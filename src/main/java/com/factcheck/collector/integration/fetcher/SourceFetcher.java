package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.exception.FetchException;

import java.util.List;

public interface SourceFetcher {

    List<RawArticle> fetch(Source source) throws FetchException;

    boolean supports(SourceType type);
}