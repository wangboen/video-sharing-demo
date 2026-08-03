package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.*;
import com.videosharing.videosharingspringboot.service.CategoryTagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminCategoryTagController {
    private final CategoryTagService service;

    public AdminCategoryTagController(CategoryTagService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok(service.adminCategories().stream().map(CategoryResponse::from).toList());
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@RequestBody CategoryUpsertRequest request) {
        return new ResponseEntity<>(CategoryResponse.from(service.saveCategory(null, request)), HttpStatus.CREATED);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id,
                                                           @RequestBody CategoryUpsertRequest request) {
        return ResponseEntity.ok(CategoryResponse.from(service.saveCategory(id, request)));
    }

    @PatchMapping("/categories/{id}/enabled")
    public ResponseEntity<CategoryResponse> setCategoryEnabled(@PathVariable Long id,
                                                               @RequestBody EnabledRequest request) {
        return ResponseEntity.ok(CategoryResponse.from(service.setCategoryEnabled(id, request.enabled())));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> tags(@RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(service.adminTags(categoryId).stream().map(TagResponse::from).toList());
    }

    @PostMapping("/tags")
    public ResponseEntity<TagResponse> createTag(@RequestBody TagUpsertRequest request) {
        return new ResponseEntity<>(TagResponse.from(service.saveTag(null, request)), HttpStatus.CREATED);
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<TagResponse> updateTag(@PathVariable Long id, @RequestBody TagUpsertRequest request) {
        return ResponseEntity.ok(TagResponse.from(service.saveTag(id, request)));
    }

    @PatchMapping("/tags/{id}/enabled")
    public ResponseEntity<TagResponse> setTagEnabled(@PathVariable Long id, @RequestBody EnabledRequest request) {
        return ResponseEntity.ok(TagResponse.from(service.setTagEnabled(id, request.enabled())));
    }
}
