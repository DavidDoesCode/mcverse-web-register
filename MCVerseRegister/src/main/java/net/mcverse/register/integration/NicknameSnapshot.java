package net.mcverse.register.integration;

public record NicknameSnapshot(String nickname) {

    public static String normalize(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname;
    }
}
