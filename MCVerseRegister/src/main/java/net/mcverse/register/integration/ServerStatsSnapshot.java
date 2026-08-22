package net.mcverse.register.integration;

import java.time.Instant;

public record ServerStatsSnapshot(
        Instant observedAt,
        Instant weekStart,
        Long playersJoined,
        Integer rankDefault,
        Integer rankMember,
        Integer rankRegular,
        Integer rankCitizen,
        Integer citizenAll,
        Double economyTotal,
        Integer planRegularPlayers,
        Long totalPlaytimeMs,
        Long minecraftDay,
        Double averageTps,
        Long playerKillsAllTime,
        Long deathsAllTime,
        Long mobKillsAllTime,
        Long claimedArea,
        Long playerKillsThisWeek,
        Long deathsThisWeek,
        Long mobKillsThisWeek
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant observedAt;
        private Instant weekStart;
        private Long playersJoined;
        private Integer rankDefault;
        private Integer rankMember;
        private Integer rankRegular;
        private Integer rankCitizen;
        private Integer citizenAll;
        private Double economyTotal;
        private Integer planRegularPlayers;
        private Long totalPlaytimeMs;
        private Long minecraftDay;
        private Double averageTps;
        private Long playerKillsAllTime;
        private Long deathsAllTime;
        private Long mobKillsAllTime;
        private Long claimedArea;
        private Long playerKillsThisWeek;
        private Long deathsThisWeek;
        private Long mobKillsThisWeek;

        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public Builder weekStart(Instant weekStart) {
            this.weekStart = weekStart;
            return this;
        }

        public Builder playersJoined(Long playersJoined) {
            this.playersJoined = playersJoined;
            return this;
        }

        public Builder rankDefault(Integer rankDefault) {
            this.rankDefault = rankDefault;
            return this;
        }

        public Builder rankMember(Integer rankMember) {
            this.rankMember = rankMember;
            return this;
        }

        public Builder rankRegular(Integer rankRegular) {
            this.rankRegular = rankRegular;
            return this;
        }

        public Builder rankCitizen(Integer rankCitizen) {
            this.rankCitizen = rankCitizen;
            return this;
        }

        public Builder citizenAll(Integer citizenAll) {
            this.citizenAll = citizenAll;
            return this;
        }

        public Builder economyTotal(Double economyTotal) {
            this.economyTotal = economyTotal;
            return this;
        }

        public Builder planRegularPlayers(Integer planRegularPlayers) {
            this.planRegularPlayers = planRegularPlayers;
            return this;
        }

        public Builder totalPlaytimeMs(Long totalPlaytimeMs) {
            this.totalPlaytimeMs = totalPlaytimeMs;
            return this;
        }

        public Builder minecraftDay(Long minecraftDay) {
            this.minecraftDay = minecraftDay;
            return this;
        }

        public Builder averageTps(Double averageTps) {
            this.averageTps = averageTps;
            return this;
        }

        public Double averageTps() {
            return averageTps;
        }

        public Builder playerKillsAllTime(Long playerKillsAllTime) {
            this.playerKillsAllTime = playerKillsAllTime;
            return this;
        }

        public Builder deathsAllTime(Long deathsAllTime) {
            this.deathsAllTime = deathsAllTime;
            return this;
        }

        public Builder mobKillsAllTime(Long mobKillsAllTime) {
            this.mobKillsAllTime = mobKillsAllTime;
            return this;
        }

        public Builder claimedArea(Long claimedArea) {
            this.claimedArea = claimedArea;
            return this;
        }

        public Builder playerKillsThisWeek(Long playerKillsThisWeek) {
            this.playerKillsThisWeek = playerKillsThisWeek;
            return this;
        }

        public Builder deathsThisWeek(Long deathsThisWeek) {
            this.deathsThisWeek = deathsThisWeek;
            return this;
        }

        public Builder mobKillsThisWeek(Long mobKillsThisWeek) {
            this.mobKillsThisWeek = mobKillsThisWeek;
            return this;
        }

        public ServerStatsSnapshot build() {
            return new ServerStatsSnapshot(
                    observedAt,
                    weekStart,
                    playersJoined,
                    rankDefault,
                    rankMember,
                    rankRegular,
                    rankCitizen,
                    citizenAll,
                    economyTotal,
                    planRegularPlayers,
                    totalPlaytimeMs,
                    minecraftDay,
                    averageTps,
                    playerKillsAllTime,
                    deathsAllTime,
                    mobKillsAllTime,
                    claimedArea,
                    playerKillsThisWeek,
                    deathsThisWeek,
                    mobKillsThisWeek
            );
        }
    }
}
