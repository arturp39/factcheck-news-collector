package com.factcheck.collector.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingUtilsTest {

    @Test
    void chunkSentencesGroupsEveryFourSentences() {
        List<String> sentences = List.of(
                "Sentence 1", "Sentence 2", "Sentence 3", "Sentence 4",
                "Sentence 5", "Sentence 6"
        );

        List<String> chunks = ChunkingUtils.chunkSentences(sentences, 4, 1200);

        assertThat(chunks)
                .hasSize(2)
                .containsExactly(
                        "Sentence 1 Sentence 2 Sentence 3 Sentence 4",
                        "Sentence 5 Sentence 6"
                );
    }

    @Test
    void chunkSentencesHandlesEmptyInput() {
        List<String> chunks = ChunkingUtils.chunkSentences(List.of(), 4, 1200);

        assertThat(chunks).isEmpty();
    }
}
