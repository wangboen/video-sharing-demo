package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.CategoryResponse;
import com.videosharing.videosharingspringboot.dto.TagResponse;
import com.videosharing.videosharingspringboot.service.CategoryTagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryTagService service;

    public CategoryController(CategoryTagService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok(service.publicCategories().stream().map(CategoryResponse::from).toList());
    }

    @GetMapping("/{categoryId}/tags")
    public ResponseEntity<List<TagResponse>> tags(@PathVariable Long categoryId) {
        return ResponseEntity.ok(service.publicTags(categoryId).stream().map(TagResponse::from).toList());
    }
}
