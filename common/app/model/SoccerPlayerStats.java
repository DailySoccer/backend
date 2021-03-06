package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.opta.OptaEvent;
import model.opta.OptaEventType;
import model.opta.OptaMatchEventStats;
import org.bson.types.ObjectId;
import play.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class SoccerPlayerStats {

    @JsonView(JsonViews.NotForClient.class)
    public String optaPlayerId;

    public String optaCompetitionId;

    @JsonView(JsonViews.Statistics.class)
    public String optaMatchEventId;

    public Date startDate;

    @JsonView(JsonViews.Statistics.class)
    public ObjectId teamId;

    @JsonView(JsonViews.Statistics.class)
    public ObjectId opponentTeamId;

    public int fantasyPoints;

    @JsonView(JsonViews.Statistics.class)
    public int playedMinutes;

    @JsonView(JsonViews.Statistics.class)
    public HashMap<String, Integer> statsCount = new HashMap<>();   // SoccerPlayerStatType => num de veces que ha ocurrido

    @JsonView(JsonViews.NotForClient.class)
    public HashMap<String, Integer> eventsCount = null;   // OptaEventType => num de veces que ha ocurrido

    public SoccerPlayerStats() { }

    public SoccerPlayerStats(Date startDate, String optaPlayerId, String optaCompetitionId, String optaMatchEventId, ObjectId teamId, ObjectId opponentTeamId, int fantasyPoints) {
        this.optaCompetitionId = optaCompetitionId;
        this.optaMatchEventId = optaMatchEventId;
        this.optaPlayerId = optaPlayerId;

        this.startDate = startDate;
        this.teamId = teamId;
        this.opponentTeamId = opponentTeamId;
        this.fantasyPoints = fantasyPoints;

        init();
    }

    public int getStatCount(SoccerPlayerStatType statType) {
        if (statsCount.containsKey(statType.toString())) {
            return statsCount.get(statType.toString());
        }
        return 0;
    }

    public boolean hasPlayed() {
        return (playedMinutes > 0 || fantasyPoints != 0 || !statsCount.isEmpty());
    }

    public void updateEventStats() {
        eventsCount = new HashMap<>();
        for (OptaEventType optaEventType : OptaEventType.values()) {
            int count = countStat(optaEventType.code);
            if (count > 0) {
                eventsCount.put(optaEventType.toString(), count);
            }
        }
        Model.templateSoccerPlayers().update("{stats: { $elemMatch: { optaPlayerId: #, optaMatchEventId: # } } }", optaPlayerId, optaMatchEventId)
                .with("{$set: {\"stats.$.eventsCount\": #}}", eventsCount);
        // Logger.debug("EventStats: {} optaMatchEventId: {}", optaPlayerId, optaMatchEventId);
    }

    private void init() {
        // Logger.debug("Stats: {} -----", optaPlayerId);

        // TODO: Verificar si matchEventStats puede ser NULL
        OptaMatchEventStats matchEventStats = OptaMatchEventStats.findOne(optaMatchEventId);
        playedMinutes = (matchEventStats != null) ? matchEventStats.getPlayedMinutes(optaPlayerId) : 0;

        // Contabilizar los eventos: Opta los manda de uno en uno y aqui es donde los agregamos
        for (SoccerPlayerStatType statType : SoccerPlayerStatType.values()) {
            int count = countStat(statType);
            if (count > 0) {
                statsCount.put(statType.toString(), count);

                // Logger.debug("Stat: {} : {} count", statType, count);
            }
        }
    }

    private int countStat(SoccerPlayerStatType statType) {
        int count = 0;

        List<Integer> eventsTypes = statType.getEventTypes();

        // Los goles encajados tienen un factor de "times" en sus eventos
        if (statType.equals(SoccerPlayerStatType.GOLES_ENCAJADOS)) {
            for (Integer eventType : statType.getEventTypes()) {
                count += countStat(eventType);
            }
        }
        else {
            count = (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId:  {$in: #}}", optaPlayerId, optaMatchEventId, eventsTypes);
        }

        return count;
    }

    private int countStat(Integer eventType) {
        int count = 0;

        if (eventType == OptaEventType.GOAL_CONCEDED.code) {
            OptaEvent goalConcededEvent = Model.optaEvents().findOne("{optaPlayerId: #, gameId: #, typeId: #}", optaPlayerId, optaMatchEventId, eventType).as(OptaEvent.class);
            if (goalConcededEvent != null) {
                count = goalConcededEvent.times != null ? goalConcededEvent.times : 1;
            }
        }
        else {
            count = (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId: #}", optaPlayerId, optaMatchEventId, eventType);
        }

        return count;
    }
}
