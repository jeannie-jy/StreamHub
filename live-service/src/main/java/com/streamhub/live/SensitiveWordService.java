package com.streamhub.live;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordService {
    private static final String WORD_KEY = "moderation:sensitive-words";
    private static final String LOADED_KEY = "moderation:sensitive-words:loaded";

    private final SensitiveWordRepository repository;
    private final StringRedisTemplate redisTemplate;

    public SensitiveWordService(SensitiveWordRepository repository, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> matchedWord(String content) {
        loadIfNeeded();
        var members = redisTemplate.opsForSet().members(WORD_KEY);
        List<String> words = members == null ? List.of() : members.stream().toList();
        return words.stream().filter(content::contains).findFirst();
    }

    public List<SensitiveWord> list() {
        return repository.findActive();
    }

    public SensitiveWord add(String word) {
        SensitiveWord result = repository.create(word.trim());
        invalidate();
        return result;
    }

    public void remove(long id) {
        repository.delete(id);
        invalidate();
    }

    public void logBlocked(long roomId, long userId, String content, String matchedWord) {
        repository.logBlocked(roomId, userId, content, matchedWord);
    }

    private void loadIfNeeded() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOADED_KEY))) {
            return;
        }
        List<SensitiveWord> words = repository.findActive();
        redisTemplate.delete(WORD_KEY);
        if (!words.isEmpty()) {
            redisTemplate.opsForSet().add(WORD_KEY, words.stream().map(SensitiveWord::word).toArray(String[]::new));
        }
        redisTemplate.opsForValue().set(LOADED_KEY, "1", Duration.ofHours(1));
    }

    private void invalidate() {
        redisTemplate.delete(List.of(WORD_KEY, LOADED_KEY));
    }
}
