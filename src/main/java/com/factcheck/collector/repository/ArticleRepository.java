package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("""
           select a.externalUrl
           from Article a
           where a.externalUrl in :urls
           """)
    Set<String> findExistingUrls(@Param("urls") Set<String> urls);
}
