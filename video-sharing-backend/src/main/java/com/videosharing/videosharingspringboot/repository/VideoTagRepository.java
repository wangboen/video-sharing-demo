package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.VideoTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoTagRepository extends JpaRepository<VideoTag, Long> {
    boolean existsByCode(String code);
    List<VideoTag> findAllByCategoryIdAndEnabledTrueOrderBySortOrderAscIdAsc(Long categoryId);
    List<VideoTag> findAllByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);
}
