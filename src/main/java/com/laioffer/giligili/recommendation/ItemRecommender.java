package com.laioffer.giligili.recommendation;

import com.laioffer.giligili.db.MySQLConnection;
import com.laioffer.giligili.db.MySQLException;
import com.laioffer.giligili.entity.Game;
import com.laioffer.giligili.entity.Item;
import com.laioffer.giligili.entity.ItemType;
import com.laioffer.giligili.external.TwitchClient;
import com.laioffer.giligili.external.TwitchException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ItemRecommender {
    private static final int DEFAULT_GAME_LIMIT = 3;
    private static final int DEFAULT_PER_GAME_RECOMMENDATION_LIMIT = 10;
    private static final int DEFAULT_TOTAL_RECOMMENDATION_LIMIT = 20;

    // Return a list of Item objects for the given type. Types are one of [Stream, Video, Clip]. Add items are related to the top games provided in the argument
    private List<Item> recommendByTopGames(ItemType type, List<Game> topGames) throws RecommendationException {
        List<Item> recommendedItems = new ArrayList<>();
        TwitchClient client = new TwitchClient();

        outerloop:
        for (Game game : topGames) {
            List<Item> items;
            try {
                items = client.searchByType(game.getId(), type, DEFAULT_PER_GAME_RECOMMENDATION_LIMIT);
            } catch (TwitchException e) {
                throw new RecommendationException("Failed to get recommendation result");
            }
            for (Item item : items) {
                if (recommendedItems.size() == DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
                    break outerloop;
                }
                recommendedItems.add(item);
            }
        }
        return recommendedItems;
    }

    // Return a list of Item objects for the given type. Types are one of [Stream, Video, Clip]. All items are related to the items previously favorited by the user. E.g., if a user favorited some videos about game "Just Chatting", then it will return some other videos about the same game.
    private List<Item> recommendByFavoriteHistory(Set<String> favoritedItemIds, List<String> favoriteGameIds, ItemType type) throws RecommendationException {
        // {123, 123, 321, 231, 123, 321} -> {123 : 3, 231 : 1, 321 : 2}
        Map<String, Long> favoriteGameIdCount = favoriteGameIds.parallelStream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // {123 : 3, 231 : 1, 321 : 2} -> {123 : 3, 321 : 2, 231 : 1}
        List<Map.Entry<String, Long>> sortedFavoriteGameIdByCount = new ArrayList<>(favoriteGameIdCount.entrySet());
        sortedFavoriteGameIdByCount.sort((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()));
        if (sortedFavoriteGameIdByCount.size() > DEFAULT_GAME_LIMIT) {
            sortedFavoriteGameIdByCount = sortedFavoriteGameIdByCount.subList(0, DEFAULT_GAME_LIMIT);
        }

        List<Item> recommendedItems = new ArrayList<>();
        TwitchClient client = new TwitchClient();
        outerloop:
        for (Map.Entry<String, Long> favoriteGame : sortedFavoriteGameIdByCount) {
            List<Item> items;
            try {
                items = client.searchByType(favoriteGame.getKey(), type, DEFAULT_PER_GAME_RECOMMENDATION_LIMIT);
            } catch (TwitchException e) {
                throw new RecommendationException("Failed to get recommendation result");
            }

            for (Item item : items) {
                if (recommendedItems.size() == DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
                    break outerloop;
                }
                if (!favoritedItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                }
            }
        }
        return recommendedItems;
    }

    // Return a map of Item objects as the recommendation result. Keys of the may are [Stream, Video, Clip]. Each key is corresponding to a list of Items objects, each item object is a recommended item based on the top games currently on Twitch.
    public Map<String, List<Item>> recommendItemsByDefault() throws RecommendationException {
        Map<String, List<Item>> recommendedItemMap = new HashMap<>();
        TwitchClient client = new TwitchClient();
        List<Game> topGames;
        try {
            topGames = client.topGames(DEFAULT_GAME_LIMIT);
        } catch (TwitchException e) {
            throw new RecommendationException("Failed to get game data for recommendation");
        }

        for (ItemType type : ItemType.values()) {
            recommendedItemMap.put(type.toString(), recommendByTopGames(type, topGames));
        }
        return recommendedItemMap;
    }

    // Return a map of Item objects as the recommendation result. Keys of the may are [Stream, Video, Clip]. Each key is corresponding to a list of Items objects, each item object is a recommended item based on the previous favorite records by the user.
    public Map<String, List<Item>> recommendItemsByUser(String userId) throws RecommendationException {
        Map<String, List<Item>> recommendedItemMap = new HashMap<>();
        Set<String> favoriteItemIds;
        Map<String, List<String>> favoriteGameIds;
        MySQLConnection connection = null;
        try {
            connection = new MySQLConnection();
            favoriteItemIds = connection.getFavoriteItemIds(userId);
            favoriteGameIds = connection.getFavoriteGameIds(favoriteItemIds);
        } catch (MySQLException e) {
            throw new RecommendationException("Failed to get user favorite history for recommendation");
        } finally {
            connection.close();
        }

        for (Map.Entry<String, List<String>> gameId : favoriteGameIds.entrySet()) {
            if (gameId.getValue().size() == 0) {
                TwitchClient client = new TwitchClient();
                List<Game> topGames;
                try {
                    topGames = client.topGames(DEFAULT_GAME_LIMIT);
                } catch (TwitchException e) {
                    throw new RecommendationException("Failed to get game data for recommendation");
                }
                recommendedItemMap.put(gameId.getKey(), recommendByTopGames(ItemType.valueOf(gameId.getKey()), topGames));
            } else {
                recommendedItemMap.put(gameId.getKey(), recommendByFavoriteHistory(favoriteItemIds, gameId.getValue(), ItemType.valueOf(gameId.getKey())));
            }
        }
        return recommendedItemMap;
    }
}
