package com.videosharing.videosharingspringboot.recommendation;

import java.util.List;

public record InterestProfile(List<WeightedId> items) {
    public InterestProfile {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isRich() {
        return items.size() >= 3;
    }
}
