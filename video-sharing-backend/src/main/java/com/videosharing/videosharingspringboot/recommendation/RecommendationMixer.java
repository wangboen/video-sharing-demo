package com.videosharing.videosharingspringboot.recommendation;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class RecommendationMixer {

    public List<Long> mix(RecommendationRequest request,
                          List<CandidatePoolResult> pools,
                          List<Long> hotFallbackIds) {
        LinkedHashSet<Long> selected = new LinkedHashSet<>();
        Map<CandidatePoolType, List<Long>> byType = byType(pools);
        for (var allocation : allocations(request)) {
            addFromPool(selected, byType.get(allocation.type()), allocation.limit(), request.size());
        }
        addFromPool(selected, hotFallbackIds, request.size(), request.size());
        return selected.stream().limit(request.size()).toList();
    }

    private static Map<CandidatePoolType, List<Long>> byType(List<CandidatePoolResult> pools) {
        Map<CandidatePoolType, List<Long>> byType = new EnumMap<>(CandidatePoolType.class);
        if (pools == null) {
            return byType;
        }
        for (CandidatePoolResult pool : pools) {
            byType.put(pool.type(), pool.videoIds());
        }
        return byType;
    }

    private static List<Allocation> allocations(RecommendationRequest request) {
        if (!request.loggedIn()) {
            return List.of(
                    new Allocation(CandidatePoolType.HOT, 6),
                    new Allocation(CandidatePoolType.FRESH, 3),
                    new Allocation(CandidatePoolType.EXPLORE, 1)
            );
        }
        if (!request.richHistory()) {
            return List.of(
                    new Allocation(CandidatePoolType.HOT, 5),
                    new Allocation(CandidatePoolType.FRESH, 3),
                    new Allocation(CandidatePoolType.CATEGORY_INTEREST, 1),
                    new Allocation(CandidatePoolType.TAG_INTEREST, 1),
                    new Allocation(CandidatePoolType.EXPLORE, 1)
            );
        }
        return List.of(
                new Allocation(CandidatePoolType.CATEGORY_INTEREST, 3),
                new Allocation(CandidatePoolType.TAG_INTEREST, 3),
                new Allocation(CandidatePoolType.HOT, 2),
                new Allocation(CandidatePoolType.FRESH, 2),
                new Allocation(CandidatePoolType.CONTINUE_WATCHING, 1),
                new Allocation(CandidatePoolType.EXPLORE, 1)
        );
    }

    private static void addFromPool(LinkedHashSet<Long> selected, List<Long> ids, int limit, int size) {
        if (ids == null || selected.size() >= size) {
            return;
        }
        int considered = 0;
        for (Long id : ids) {
            if (id == null || considered >= limit || selected.size() >= size) {
                break;
            }
            considered++;
            selected.add(id);
        }
    }

    private record Allocation(CandidatePoolType type, int limit) {
    }
}
