package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    public ObjectId userId;
    public String nickName;

    @JsonView(JsonViews.Public.class)
    public String facebookID;

    @JsonView(JsonViews.NotForClient.class)
    public int wins;

    public int trueSkill = 0;

    @JsonView(JsonViews.NotForClient.class)
    public float managerLevel = 0;

    @JsonView(JsonViews.NotForClient.class)
    public Money earnedMoney = MoneyUtils.zero;

    @JsonView(JsonViews.Public.class)
    public List<String> achievements = new ArrayList<>();

    public UserInfo() {}

    public UserInfo(User theUser) {
        this.userId = theUser.userId;
        this.nickName = theUser.nickName;
        this.wins = theUser.wins;
        this.trueSkill = theUser.trueSkill;
        this.managerLevel = User.managerLevelFromPoints(theUser.managerBalance);
        this.earnedMoney = theUser.earnedMoney;
        this.facebookID = theUser.facebookID;
    }

    public UserInfo(ObjectId userId, String nickName, int wins, int trueSkill, Money earnedMoney) {
        this.userId = userId;
        this.nickName = nickName;
        this.wins = wins;
        this.trueSkill = trueSkill;
        this.earnedMoney = earnedMoney;
    }

    static public List<UserInfo> findAll() {
        List<UserInfo> usersInfo = new ArrayList<>();

        User.findAll(JsonViews.Leaderboard.class).forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }

    static public List<UserInfo> findAllWithAchievements() {
        List<UserInfo> usersInfo = new ArrayList<>();

        User.findAll(JsonViews.Leaderboard.class).forEach(user -> usersInfo.add(user.infoWithAchievements()));

        return usersInfo;
    }

    static public List<UserInfo> findGuildWithAchievements(ObjectId guildId) {
        List<UserInfo> usersInfo = new ArrayList<>();

        if (guildId != null) {
            User.findByGuild(guildId).forEach(user -> usersInfo.add(user.infoWithAchievements()));
        }

        return usersInfo;
    }

    static public List<UserInfo> findAllFromContestEntries(List<ContestEntry> contestEntries) {
        List<UserInfo> usersInfo = new ArrayList<>(contestEntries.size());

        User.find(contestEntries).forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }

    static public List<UserInfo> findTrueSkillFromContestEntries(List<ContestEntry> contestEntries) {
        List<UserInfo> usersInfo = new ArrayList<>(contestEntries.size());

        User.find(contestEntries, "{ nickName: 1, trueSkill: 1 }").forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }

    static public List<UserInfo> findNicknamesFromContestEntries(List<ContestEntry> contestEntries) {
        List<UserInfo> usersInfo = new ArrayList<>(contestEntries.size());

        User.find(contestEntries, "{ nickName: 1 }").forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }
}