CREATE SCHEMA IF NOT EXISTS content;

-- TABLE: sources
CREATE TABLE content.sources
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255)     NOT NULL,
    type              VARCHAR(50)      NOT NULL,
    url               TEXT             NOT NULL,
    category          VARCHAR(100)     NOT NULL DEFAULT 'general',
    enabled           BOOLEAN          NOT NULL DEFAULT TRUE,
    reliability_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,


    last_fetched_at   TIMESTAMPTZ,
    last_success_at   TIMESTAMPTZ,
    failure_count     INT              NOT NULL DEFAULT 0,

    created_at        TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_sources_url
    ON content.sources (url);


-- TABLE: articles
CREATE TABLE content.articles
(
    id               BIGSERIAL PRIMARY KEY,
    source_id        BIGINT      NOT NULL REFERENCES content.sources (id),
    external_url     TEXT        NOT NULL,
    title            TEXT        NOT NULL,
    description      TEXT,
    published_date   TIMESTAMPTZ,
    fetched_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    chunk_count      INT         NOT NULL DEFAULT 0,
    status           VARCHAR(50) NOT NULL,
    error_message    TEXT,
    weaviate_indexed BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_articles_external_url
    ON content.articles (external_url);

CREATE INDEX ix_articles_source_id
    ON content.articles (source_id);


-- TABLE: ingestion_logs
CREATE TABLE content.ingestion_logs
(
    id                 BIGSERIAL PRIMARY KEY,
    source_id          BIGINT REFERENCES content.sources (id),
    started_at         TIMESTAMPTZ NOT NULL,
    completed_at       TIMESTAMPTZ,
    articles_fetched   INT         NOT NULL DEFAULT 0,
    articles_processed INT         NOT NULL DEFAULT 0,
    articles_failed    INT         NOT NULL DEFAULT 0,
    status             VARCHAR(50),
    error_details      TEXT,
    correlation_id     VARCHAR(36) NOT NULL
);

CREATE INDEX ix_ingestion_logs_source_id
    ON content.ingestion_logs (source_id);