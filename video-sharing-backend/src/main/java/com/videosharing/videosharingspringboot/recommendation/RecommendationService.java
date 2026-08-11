package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final VideoRepository repository;
    private final HotCandidateProvider hot;
    private final FreshCandidateProvider fresh;
    private final ExploreCandidateProvider explore;
    private final CategoryInterestCandidateProvider category;
    private final TagInterestCandidateProvider tag;
    private final ContinueWatchingCandidateProvider watching;
    private final InterestProfileService profiles;
    private final RecommendationMixer mixer;

    public RecommendationService(VideoRepository repository,
                                 HotCandidateProvider hot,
                                 FreshCandidateProvider fresh,
                                 ExploreCandidateProvider explore,
                                 CategoryInterestCandidateProvider category,
                                 TagInterestCandidateProvider tag,
                                 ContinueWatchingCandidateProvider watching,
                                 InterestProfileService profiles,
                                 RecommendationMixer mixer) {
        this.repository = repository;
        this.hot = hot;
        this.fresh = fresh;
        this.explore = explore;
        this.category = category;
        this.tag = tag;
        this.watching = watching;
        this.profiles = profiles;
        this.mixer = mixer;
    }

    public Page<Video> recommend(User user, Pageable pageable, Long categoryId, Long tagId) {
        if (tagId != null) {
            return repository.findRecommendedByTag(VideoStatus.APPROVED, tagId, pageable);
        }
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 12 : pageable.getPageSize();
        Long userId = user == null ? null : user.getId();
        boolean loggedIn = userId != null;
        boolean richHistory = loggedIn && richHistory(userId);
        RecommendationRequest request = RecommendationRequest.home(userId, page, size, categoryId, tagId,
                loggedIn, richHistory);
        int providerLimit = Math.max(size * 3, 30);
        int fallbackLimit = Math.max(size * 4, 50);

        List<CandidatePoolResult> pools = new ArrayList<>();
        if (loggedIn) {
            pools.add(safe(CandidatePoolType.CATEGORY_INTEREST, () -> category.candidates(request, providerLimit)));
            pools.add(safe(CandidatePoolType.TAG_INTEREST, () -> tag.candidates(request, providerLimit)));
            pools.add(safe(CandidatePoolType.CONTINUE_WATCHING, () -> watching.candidates(request, providerLimit)));
        }
        pools.add(safe(CandidatePoolType.HOT, () -> hot.candidates(request, providerLimit)));
        pools.add(safe(CandidatePoolType.FRESH, () -> fresh.candidates(request, providerLimit)));
        pools.add(safe(CandidatePoolType.EXPLORE, () -> explore.candidates(request, providerLimit)));

        List<Long> fallback = categoryId == null
                ? repository.findHotCandidateIds(VideoStatus.APPROVED, PageRequest.of(0, fallbackLimit))
                : repository.findHotCandidateIdsByCategory(VideoStatus.APPROVED, categoryId,
                        PageRequest.of(0, fallbackLimit));
        List<Long> ids = mixer.mix(request, pools, fallback);
        List<Video> ordered = loadInOrder(ids);
        Pageable effectivePageable = pageable == null ? PageRequest.of(page, size) : pageable;
        return new PageImpl<>(ordered, effectivePageable, ordered.size());
    }

    private boolean richHistory(Long userId) {
        return profiles.categoryProfile(userId).items().size() + profiles.tagProfile(userId).items().size() >= 3;
    }

    private CandidatePoolResult safe(CandidatePoolType type, Supplier<CandidatePoolResult> supplier) {
        try {
            CandidatePoolResult result = supplier.get();
            return result == null ? new CandidatePoolResult(type, List.of()) : result;
        } catch (RuntimeException exception) {
            log.warn("Recommendation provider degraded pool={}", type, exception);
            return new CandidatePoolResult(type, List.of());
        }
    }

    private List<Video> loadInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Video> byId = new LinkedHashMap<>();
        for (Video video : repository.findAllByIdIn(ids)) {
            byId.put(video.getId(), video);
        }
        return ids.stream()
                .map(byId::get)
                .filter(video -> video != null && video.getStatus() == VideoStatus.APPROVED)
                .toList();
    }
}
