package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.VideoCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoCategoryRepository extends JpaRepository<VideoCategory, Long> {
    Optional<VideoCategory> findByCode(String code);
    List<VideoCategory> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    List<VideoCategory> findAllByOrderBySortOrderAscIdAsc();
}
