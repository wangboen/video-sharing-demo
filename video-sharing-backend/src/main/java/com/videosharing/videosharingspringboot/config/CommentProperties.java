package com.videosharing.videosharingspringboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "comments")
public class CommentProperties {
    private int featuredLimit = 3;
    private long featuredMinLikes = 10;
    private int pageSizeDefault = 20;
    private int pageSizeMax = 50;
    private int maxLength = 1000;
    private int hotRefreshBatchSize = 500;
    private long hotCacheTtlSeconds = 60;

    public int getFeaturedLimit() { return featuredLimit; }
    public void setFeaturedLimit(int featuredLimit) { this.featuredLimit = featuredLimit; }
    public long getFeaturedMinLikes() { return featuredMinLikes; }
    public void setFeaturedMinLikes(long featuredMinLikes) { this.featuredMinLikes = featuredMinLikes; }
    public int getPageSizeDefault() { return pageSizeDefault; }
    public void setPageSizeDefault(int pageSizeDefault) { this.pageSizeDefault = pageSizeDefault; }
    public int getPageSizeMax() { return pageSizeMax; }
    public void setPageSizeMax(int pageSizeMax) { this.pageSizeMax = pageSizeMax; }
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
    public int getHotRefreshBatchSize() { return hotRefreshBatchSize; }
    public void setHotRefreshBatchSize(int hotRefreshBatchSize) { this.hotRefreshBatchSize = hotRefreshBatchSize; }
    public long getHotCacheTtlSeconds() { return hotCacheTtlSeconds; }
    public void setHotCacheTtlSeconds(long hotCacheTtlSeconds) { this.hotCacheTtlSeconds = hotCacheTtlSeconds; }
}
