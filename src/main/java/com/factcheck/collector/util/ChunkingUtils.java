package com.factcheck.collector.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ChunkingUtils {

    public List<String> chunkSentences(
            List<String> sentences,
            int sentencesPerChunk,
            int maxCharactersPerChunk
    ) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (String s : sentences) {
            if (s == null || s.isBlank()) {
                continue;
            }
            if (count > 0) {
                current.append(" ");
            }
            current.append(s);
            count++;

            boolean sentenceLimitReached = count >= sentencesPerChunk;
            boolean sizeLimitReached = current.length() >= maxCharactersPerChunk;

            if (sentenceLimitReached || sizeLimitReached) {
                chunks.add(current.toString());
                current.setLength(0);
                count = 0;
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
