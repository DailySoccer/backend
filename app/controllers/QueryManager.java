package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import play.Logger;
import play.libs.Akka;
import play.libs.F;
import play.libs.F.Promise;
import scala.concurrent.duration.Duration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static akka.pattern.Patterns.ask;

public class QueryManager {
    private final static int ACTOR_TIMEOUT = 10000;
    private final static String CACHE_DISPATCHER = "cache-dispatcher";
    private final static int CHECK_ACTIVE_TEMPLATE_CONTESTS = 15 * 60;      // 15 minutos

    private QueryManager() {}

    static Promise<Object> getActiveTemplateContests() {
        ActorRef actor = getActor("activetemplatecontests", ref ->
            Akka.system().scheduler().schedule(
                    Duration.create(CHECK_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS),     //Initial delay
                    Duration.create(CHECK_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS),     //Frequency
                    ref,
                    "checkActiveTemplateContests",
                    Akka.system().dispatcher(),
                    null)
        );

        return F.Promise.wrap(ask(actor, "getActiveTemplateContests", ACTOR_TIMEOUT));
    }

    static Promise<Object> getActiveContestsV2() {
        ActorRef actor = getActor("activecontests" );
        return F.Promise.wrap(ask(actor, "getActiveContestsV2", ACTOR_TIMEOUT));
    }

    static Promise<Object> getActiveContestV2(String contestId) {
        String actorName = "activecontest-".concat(contestId);
        ActorRef actor = getActor( actorName );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getActiveContestV2", contestId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getMyLiveContestsV2(String userId) {
        ActorRef actor = getActor("livecontests" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getMyLiveContestsV2", userId, null), ACTOR_TIMEOUT));
    }

    static Promise<Object> getMyLiveContestV2(String userId, String contestId) {
        ActorRef actor = getActor( "livecontests" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getMyLiveContestV2", userId, contestId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getLiveMatchEventsFromTemplateContest(String templateContestId) {
        String actorName = "livematchevents".concat(templateContestId);
        ActorRef actor = getActor( actorName );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getLiveMatchEventsFromTemplateContest", templateContestId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getLiveContestEntries(String contestId) {
        ActorRef actor = getActor( "livecontestentries" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getLiveContestEntries", contestId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getContestInfoV2(String contestId) {
        String actorName = "contestinfo-".concat(contestId);
        ActorRef actor = getActor(actorName );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getContestInfoV2", contestId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getTemplateSoccerPlayersV2() {
        ActorRef actor = getActor("templatesoccerplayers" );
        return F.Promise.wrap(ask(actor, "getTemplateSoccerPlayersV2", ACTOR_TIMEOUT));
    }

    static Promise<Object> getTemplateSoccerPlayerInfo(String templateSoccerPlayerId) {
        ActorRef actor = getActor("templatesoccerplayers" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getTemplateSoccerPlayerInfo", templateSoccerPlayerId), ACTOR_TIMEOUT));
    }

    static Promise<Object> getTemplateSoccerTeams() {
        ActorRef actor = getActor("templatesoccerteams" );
        return F.Promise.wrap(ask(actor, "getTemplateSoccerTeams", ACTOR_TIMEOUT));
    }

    static Promise<Object> getSoccerPlayersByCompetition(String competitionId) {
        ActorRef actor = getActor("templatesoccerplayers" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getSoccerPlayersByCompetition", competitionId), ACTOR_TIMEOUT));
    }

    static Promise<Object> countActiveTemplateContests() {
        ActorRef actor = getActor("activecontests" );
        return F.Promise.wrap(ask(actor, "countActiveTemplateContests", ACTOR_TIMEOUT));
    }

    static Promise<Object> existsContestInLive() {
        ActorRef actor = getActor("livecontests" );
        return F.Promise.wrap(ask(actor, "existsContestInLive", ACTOR_TIMEOUT));
    }

    static Promise<Object> getUserRankingList(String userId) {
        ActorRef actor = getActor("userRanking" );
        return F.Promise.wrap(ask(actor, new QueryActor.CacheMsg("getUserRankingList", userId, null), ACTOR_TIMEOUT));
    }

    private static ActorRef getActor(String name) {
        return getActor(name, ref -> {});
    }

    private static ActorRef getActor(String name, Consumer<ActorRef> setup) {
        Supplier<ActorRef> newInstance = () -> {
            ActorRef response = Akka.system().actorOf(Props.create(QueryActor.class).withDispatcher(CACHE_DISPATCHER), name);
            setup.accept(response);
            return response;
        };

        ActorRef actor = _actors.computeIfAbsent(name, key -> newInstance.get() );
        if (actor.isTerminated()) {
            Logger.error("CacheManager: Actor \"{}\" terminated", name);
            actor = newInstance.get();
            _actors.put(name, actor);
        }
        return actor;
    }

    private static ConcurrentHashMap<String, ActorRef> _actors = new ConcurrentHashMap<>();
}
