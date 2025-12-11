package com.factcheck.collector.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ChunkingUtils {

    private static final int DEFAULT_SENTENCES_PER_CHUNK = 4;

    public List<String> chunkSentences(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (String s : sentences) {
            if (count > 0) {
                current.append(" ");
            }
            current.append(s);
            count++;

            if (count >= DEFAULT_SENTENCES_PER_CHUNK) {
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
