package com.factcheck.collector.repository;

import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByExternalUrl(String externalUrl);

    List<Article> findByStatus(ArticleStatus status);

    List<Article> findBySourceAndStatus(Source source, ArticleStatus status);
}
