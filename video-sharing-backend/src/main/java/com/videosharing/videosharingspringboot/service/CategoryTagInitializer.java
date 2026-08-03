package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoTag;
import com.videosharing.videosharingspringboot.repository.VideoCategoryRepository;
import com.videosharing.videosharingspringboot.repository.VideoTagRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CategoryTagInitializer implements ApplicationRunner {
    private final VideoCategoryRepository categories;
    private final VideoTagRepository tags;

    public CategoryTagInitializer(VideoCategoryRepository categories, VideoTagRepository tags) {
        this.categories = categories;
        this.tags = tags;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        VideoCategory movie = category("movie", "电影", 10);
        VideoCategory tv = category("tv", "电视剧", 20);
        VideoCategory anime = category("anime", "动漫", 30);
        VideoCategory game = category("game", "游戏", 40);
        VideoCategory tech = category("tech", "科技", 50);
        VideoCategory sports = category("sports", "体育", 60);

        tag(movie, "movie_action", "动作", 10);
        tag(movie, "movie_comedy", "喜剧", 20);
        tag(movie, "movie_sci_fi", "科幻", 30);
        tag(movie, "movie_suspense", "悬疑", 40);
        tag(movie, "movie_romance", "爱情", 50);
        tag(movie, "movie_crime", "犯罪", 60);
        tag(movie, "movie_horror", "恐怖", 70);
        tag(movie, "movie_review", "影评", 80);
        tag(movie, "movie_explained", "电影解说", 90);
        tag(movie, "movie_trailer", "预告片", 100);
        tag(movie, "movie_classic", "经典电影", 110);

        tag(tv, "tv_cn", "国产剧", 10);
        tag(tv, "tv_us", "美剧", 20);
        tag(tv, "tv_kr", "韩剧", 30);
        tag(tv, "tv_jp", "日剧", 40);
        tag(tv, "tv_costume", "古装", 50);
        tag(tv, "tv_urban", "都市", 60);
        tag(tv, "tv_suspense", "悬疑", 70);
        tag(tv, "tv_romance", "爱情", 80);
        tag(tv, "tv_comedy", "喜剧", 90);
        tag(tv, "tv_explained", "剧集解说", 100);
        tag(tv, "tv_trailer", "预告片", 110);

        tag(anime, "anime_hot_blood", "热血", 10);
        tag(anime, "anime_romance", "恋爱", 20);
        tag(anime, "anime_fantasy", "奇幻", 30);
        tag(anime, "anime_adventure", "冒险", 40);
        tag(anime, "anime_comedy", "搞笑", 50);
        tag(anime, "anime_healing", "治愈", 60);
        tag(anime, "anime_campus", "校园", 70);
        tag(anime, "anime_battle", "战斗", 80);
        tag(anime, "anime_cn", "国漫", 90);
        tag(anime, "anime_jp", "日漫", 100);
        tag(anime, "anime_explained", "动漫解说", 110);
        tag(anime, "anime_new", "新番", 120);

        tag(game, "game_live", "实况", 10);
        tag(game, "game_guide", "攻略", 20);
        tag(game, "game_esports", "赛事", 30);
        tag(game, "game_commentary", "解说", 40);
        tag(game, "game_console", "主机游戏", 50);
        tag(game, "game_mobile", "手游", 60);
        tag(game, "game_pc", "PC游戏", 70);
        tag(game, "game_action", "动作游戏", 80);
        tag(game, "game_rpg", "角色扮演", 90);
        tag(game, "game_fps", "射击游戏", 100);
        tag(game, "game_strategy", "策略游戏", 110);
        tag(game, "game_review", "游戏测评", 120);

        tag(tech, "tech_ai", "AI", 10);
        tag(tech, "tech_programming", "编程", 20);
        tag(tech, "tech_digital", "数码", 30);
        tag(tech, "tech_software", "软件", 40);
        tag(tech, "tech_hardware", "硬件", 50);
        tag(tech, "tech_internet", "互联网", 60);
        tag(tech, "tech_phone", "手机", 70);
        tag(tech, "tech_computer", "电脑", 80);
        tag(tech, "tech_tutorial", "教程", 90);
        tag(tech, "tech_news", "科技资讯", 100);
        tag(tech, "tech_review", "产品测评", 110);
        tag(tech, "tech_opensource", "开源项目", 120);

        tag(sports, "sports_basketball", "篮球", 10);
        tag(sports, "sports_football", "足球", 20);
        tag(sports, "sports_table_tennis", "乒乓球", 30);
        tag(sports, "sports_badminton", "羽毛球", 40);
        tag(sports, "sports_tennis", "网球", 50);
        tag(sports, "sports_running", "跑步", 60);
        tag(sports, "sports_fitness", "健身", 70);
        tag(sports, "sports_martial_arts", "格斗", 80);
        tag(sports, "sports_swimming", "游泳", 90);
        tag(sports, "sports_events", "体育赛事", 100);
        tag(sports, "sports_highlights", "比赛集锦", 110);
        tag(sports, "sports_training", "训练教学", 120);
    }

    private VideoCategory category(String code, String name, int sortOrder) {
        return categories.findByCode(code).orElseGet(() -> {
            VideoCategory category = new VideoCategory();
            category.setCode(code);
            category.setName(name);
            category.setSortOrder(sortOrder);
            return categories.save(category);
        });
    }

    private void tag(VideoCategory category, String code, String name, int sortOrder) {
        if (tags.existsByCode(code)) {
            return;
        }
        VideoTag tag = new VideoTag();
        tag.setCode(code);
        tag.setName(name);
        tag.setCategory(category);
        tag.setSortOrder(sortOrder);
        tags.save(tag);
    }
}
