package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.opta.OptaMatchEventStats;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class SoccerPlayerStats {

    @JsonView(JsonViews.NotForClient.class)
    public String optaPlayerId;

    public String optaCompetitionId;

    @JsonView(JsonViews.Statistics.class)
    public String optaMatchEventId;

    @JsonView(JsonViews.Statistics.class)
    public Date startDate;

    @JsonView(JsonViews.Statistics.class)
    public ObjectId opponentTeamId;

    public int fantasyPoints;

    @JsonView(JsonViews.Statistics.class)
    public int playedMinutes;

    @JsonView(JsonViews.Statistics.class)
    public HashMap<String, Integer> statsCount = new HashMap<>();   // SoccerPlayerStatType => num de veces que ha ocurrido

    public SoccerPlayerStats() { }

    public SoccerPlayerStats(Date startDate, String optaPlayerId, String optaCompetitionId, String optaMatchEventId, ObjectId opponentTeamId, int fantasyPoints) {
        this.optaCompetitionId = optaCompetitionId;
        this.optaMatchEventId = optaMatchEventId;
        this.optaPlayerId = optaPlayerId;

        this.startDate = startDate;
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

    private void init() {
        // Logger.debug("Stats: {} -----", optaPlayerId);

        // TODO: Verificar si matchEventStats puede ser NULL
        OptaMatchEventStats matchEventStats = OptaMatchEventStats.findOne(optaMatchEventId);
        playedMinutes = (matchEventStats != null) ? matchEventStats.getPlayedMinutes(optaPlayerId) : 0;

        // Contabilizar los eventos: Opta los manda de uno en uno y aqui es donde los agregamos
        for (SoccerPlayerStatType statType : SoccerPlayerStatType.values()) {
            int count = countStat(statType.getEventTypes());
            if (count > 0) {
                statsCount.put(statType.toString(), count);

                // Logger.debug("Stat: {} : {} count", statType, count);
            }
        }
    }

    private int countStat(List<Integer> eventsTypes) {
        return (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId:  {$in: #}}", optaPlayerId, optaMatchEventId, eventsTypes);
    }
}
