package utils;

import com.mongodb.BasicDBObject;
import model.Model;
import model.PointsTranslation;
import model.TemplateMatchEvent;
import model.LiveMatchEvent;
import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;
import org.jongo.Find;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaUtils {

    public static enum OptaEventType {
        PASS                    (1, "Any pass attempted from one player to another."),
        TAKE_ON                 (3, "Attempted dribble past an opponent" ),
        FOUL_RECIBED            (4, "Player who was fouled"),
        TACKLE                  (7, "Tackle: dispossesses an opponent of the ball, both retaining possession or not"),
        INTERCEPTION            (8, "When a player intercepts any pass event between opposition players and prevents the ball reaching its target"),
        SAVE                    (10, "Goalkeeper saves a shot on goal."),
        CLAIM                   (11, "Goalkeeper catches a crossed ball"),
        CLEARANCE               (12, "Player under pressure hits ball clear of the defensive zone or/and out of play"),
        MISS                    (13, "Shot on goal which goes wide over the goal"),
        POST                    (14, "The ball hits the frame of the goal"),
        ATTEMPT_SAVED           (15, "Shot saved, event for the player who shot the ball"),
        YELLOW_CARD             (17, "Yellow card shown to player"),
        PUNCH                   (41, "Ball is punched clear by Goalkeeper"),
        DISPOSSESSED            (50, "Player is successfully tacked and loses possession of the ball"),
        ERROR                   (51, "Mistake by player losing the ball"),
        CAUGHT_OFFSIDE          (72, "Player who is offside"),
        ASSIST                  (1210, "The pass was an assist for a shot"),
        GOAL_SCORED_BY_GOALKEEPER   (1601, "Goal scored by the goalkeeper"),
        GOAL_SCORED_BY_DEFENDER     (1602, "Goal scored by a defender"),
        GOAL_SCORED_BY_MIDFIELDER   (1603, "Goal scored by a midfielder"),
        GOAL_SCORED_BY_FORWARD      (1604, "Goal scored by a forward"),
        OWN_GOAL                (1699, "Own goal scored by the player"),
        FOUL_COMMITTED          (1004, "Player who committed the foul"),
        RED_CARD                (1017, "Red card shown to player"),
        PENALTY_COMMITTED       (1409, "Player who committed the foul (penalty)"),
        PENALTY_FAILED          (1410, "Player who shots penalty and fails"),
        GOALKEEPER_SAVES_PENALTY(1458, "Goalkeeper saves a penalty shot"),
        CLEAN_SHEET             (2000, "Clean sheet: More than 60 min played without conceding any goal"),
        GOAL_CONCEDED           (2001, "Goal conceded while player is on the field"),
        _INVALID_               (9999, "Clean sheet: More than 60 min played without conceding any goal");

        public final int code;
        public final String description;

        private OptaEventType(int c, String description){
            code = c;
            this.description = description;
        }

        public int getCode(){
            return code;
        }

        public String getDescription(){
            return description;
        }

        public static OptaEventType getEnum(int code){
            System.out.println(code);
            for (OptaEventType optaEventType: OptaEventType.values()){
                if (optaEventType.code == code) {
                    return optaEventType;
                }
            }
            return _INVALID_;
        }

        public static Map<String, String> options(){
            LinkedHashMap<String, String> vals = new LinkedHashMap<String, String>();
            for (OptaEventType eType : OptaEventType.values()) {
                if (!eType.equals(OptaEventType._INVALID_)){
                    vals.put(eType.name(), eType.name().concat(": ".concat(eType.description)));
                }
            }
            return vals;
        }

    }


    public static void processOptaDBInput(String feedType, BasicDBObject requestBody){
        resetChanges();

        if (feedType.equals("F9")){
            processF9(requestBody);
        }
        else if (feedType.equals("F24")){
            processEvents(requestBody);
        }
        else if (feedType.equals("F1")){
            processF1(requestBody);
        }

        applyChanges();
    }

    public static void processEvents(BasicDBObject gamesObj){
        try {
            LinkedHashMap games = (LinkedHashMap) gamesObj.get("Games");
            processEvents(games);
        } catch (NullPointerException e){
            e.printStackTrace();
        }


    }

    public static void processEvents(LinkedHashMap games){
        try {
            LinkedHashMap game = (LinkedHashMap) games.get("Game");

            Object events = game.get("Event");
            resetPointsTranslationCache();
            if (events instanceof ArrayList) {
                for (Object event : (ArrayList) events) {
                    processEvent((LinkedHashMap) event, game);
                }
            } else {
                processEvent((LinkedHashMap) events, game);
            }
        } catch (NullPointerException e){
            e.printStackTrace();
        }
        
    }

    public static void recalculateAllEvents(){
        resetPointsTranslationCache();
        Iterator<OptaEvent> optaEvents = (Iterator<OptaEvent>)Model.optaEvents().find().as(OptaEvent.class);
        while (optaEvents.hasNext()){
            recalculateEvent(optaEvents.next());
        }
    }

    private static void recalculateEvent(OptaEvent optaEvent){
        optaEvent.points = getPoints(optaEvent.typeId, optaEvent.timestamp);
        optaEvent.pointsTranslationId = pointsTranslationTableCache.get(optaEvent.typeId);
        Model.optaEvents().update("{eventId: #, gameId: #}", optaEvent.eventId, optaEvent.gameId).upsert().with(optaEvent);
    }

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        HashMap<Integer, Date> eventsCache = getOptaEventsCache(game.get("id").toString());
        int eventId = (int) event.get("event_id");
        Date timestamp;
        if (event.containsKey("last_modified")){
            timestamp = parseDate((String) event.get("last_modified"));
        } else {
            timestamp = parseDate((String) event.get("timestamp"));
        }
        if (eventsCache.containsKey(eventId)) {
            if (timestamp.after(eventsCache.get(eventId))) {
                updateOrInsertEvent(event, game);
                eventsCache.put(eventId, timestamp);
            }
        } else {
            updateOrInsertEvent(event, game);
            eventsCache.put(eventId, timestamp);
        }
    }
    private static void updateOrInsertEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent.optaEventId = new ObjectId();
        myEvent.gameId = game.get("id").toString();
        myEvent.homeTeamId = game.get("home_team_id").toString();
        myEvent.awayTeamId = game.get("away_team_id").toString();
        myEvent.competitionId = game.get("competition_id").toString();
        myEvent.seasonId = game.get("season_id").toString();
        myEvent.periodId = (int) event.get("period_id");
        myEvent.eventId = (int) event.get("event_id");
        myEvent.typeId = (int) event.get("type_id");
        myEvent.outcome = (int)event.get("outcome");
        myEvent.timestamp = parseDate((String)event.get("timestamp"));

        myEvent.unixtimestamp = myEvent.timestamp.getTime();
        myEvent.lastModified = parseDate((String) event.get("last_modified"));

        if (event.containsKey("player_id")){
            myEvent.optaPlayerId = event.get("player_id").toString();
        }

        if (event.containsKey("Q")){
            Object qualifierList = event.get("Q");
            if (qualifierList instanceof ArrayList) {
                myEvent.qualifiers = new ArrayList<>(((ArrayList) qualifierList).size());
                for (Object qualifier : (ArrayList) qualifierList) {
                    Integer tempQualifier = (Integer) ((LinkedHashMap) qualifier).get("qualifier_id");
                    myEvent.qualifiers.add(tempQualifier);
                }
            } else {
                myEvent.qualifiers = new ArrayList<>(1);
                Integer tempQualifier = (Integer)((LinkedHashMap)qualifierList).get("qualifier_id");
                myEvent.qualifiers.add(tempQualifier);
            }
        }
        /*
        DERIVED EVENTS GO HERE
         */
        // Asistencia
        if (myEvent.typeId==OptaEventType.PASS.code && myEvent.qualifiers.contains(210)){
                myEvent.typeId = OptaEventType.ASSIST.code;  //Asistencia -> 1210
        }
        // Falta/Penalty infligido
        else if (myEvent.typeId==OptaEventType.FOUL_RECIBED.code && myEvent.outcome==0){
            if (myEvent.qualifiers.contains(9)){
                myEvent.typeId = OptaEventType.PENALTY_COMMITTED.code;  //Penalty infligido -> 1409
            } else {
                myEvent.typeId = OptaEventType.FOUL_COMMITTED.code;  // Falta infligida -> 1004
            }
        }
        // Tarjeta roja -> 1017
        else if (myEvent.typeId==OptaEventType.YELLOW_CARD.code && myEvent.qualifiers.contains(33)){
            myEvent.typeId = OptaEventType.RED_CARD.code;
        }
        // Penalty miss -> 1410
        else if ((myEvent.typeId==OptaEventType.MISS.code || myEvent.typeId==OptaEventType.POST.code ||
                  myEvent.typeId==OptaEventType.ATTEMPT_SAVED.code) &&
                myEvent.outcome==0 && myEvent.qualifiers.contains(9)){
            myEvent.typeId = OptaEventType.PENALTY_FAILED.code;
        }
        else if (myEvent.typeId==16 && myEvent.outcome==1) {
            // Gol en contra -> 1699
            if (myEvent.qualifiers.contains(28)) {
                myEvent.typeId = OptaEventType.OWN_GOAL.code;
            } else {
            // Diferencias en goles:
                OptaPlayer scorer = Model.optaPlayers().findOne("{id: #}", "p"+myEvent.optaPlayerId).as(OptaPlayer.class);
                if (scorer.position.equals("Goalkeeper")){
                    // Gol del portero
                    myEvent.typeId = OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code;
                } else if (scorer.position.equals("Defender")){
                    // Gol del defensa
                    myEvent.typeId = OptaEventType.GOAL_SCORED_BY_DEFENDER.code;
                } else if (scorer.position.equals("Midfielder")){
                    // Gol del medio
                    myEvent.typeId = OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code;
                } else if (scorer.position.equals("Forward")){
                    // Gol del delantero
                    myEvent.typeId = OptaEventType.GOAL_SCORED_BY_FORWARD.code;
                }

            }

        }
        // Penalty parado -> 1058
        else if (myEvent.typeId==58 && !myEvent.qualifiers.contains(186)){
            myEvent.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY.code;
        }

        myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
        myEvent.pointsTranslationId = pointsTranslationTableCache.get(myEvent.typeId);

        Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).upsert().with(myEvent);
        registerChange(myEvent.gameId);
    }

    public static int getPoints(int typeId, Date timestamp) {
        if (!pointsTranslationCache.containsKey(typeId)){
            getPointsTranslation(typeId, timestamp);
        }
        return pointsTranslationCache.get(typeId);
    }

    public static PointsTranslation getPointsTranslation(int typeId, Date timestamp){
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}",
                        typeId, timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            pointsTranslationCache.put(typeId, pointsTranslation.points);
            pointsTranslationTableCache.put(typeId, pointsTranslation.pointsTranslationId);
        } else {
            pointsTranslationCache.put(typeId, 0);
            pointsTranslationTableCache.put(typeId, null);
        }
        return pointsTranslation;
    }

    private static Date parseDate(String timestamp) {
        String dateConfig = "";
        SimpleDateFormat dateFormat;
        if (timestamp.indexOf('-') > 0) {
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyy-MM-dd'T'hh:mm:ss.SSSz" : "yyyy-MM-dd hh:mm:ss.SSSz";
            dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
        }else{
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyyMMdd'T'hhmmssZ" : "yyyyMMdd hhmmssZ";
            dateFormat = new SimpleDateFormat(dateConfig);
        }
        int plusPos = timestamp.indexOf('+');
        if (plusPos>=19) {
            if (timestamp.substring(plusPos, timestamp.length()).equals("+00:00")) {
                timestamp = timestamp.substring(0, plusPos);
                dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
            } else {
                System.out.println(timestamp);
            }
        }

        Date myDate = null;
        try {
            myDate = dateFormat.parse(timestamp);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return myDate;
    }

    public static void processF1(BasicDBObject f1) {
        try {
            LinkedHashMap myF1 = (LinkedHashMap) ((LinkedHashMap) f1.get("SoccerFeed")).get("SoccerDocument");
            int competitionId = myF1.containsKey("competition_id")? (int) myF1.get("competition_id"): -1;

            ArrayList matches = myF1.containsKey("MatchData")? (ArrayList) myF1.get("MatchData"): null;
            if (matches != null){
                for (Object match: matches){

                    LinkedHashMap matchObject = (LinkedHashMap)match;
                    processMatchData(matchObject, competitionId, myF1);
                }

            }
        }catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    public static void updateOrInsertMatchData(LinkedHashMap myF1, LinkedHashMap matchObject) {
        OptaMatchEvent myOptaMatchEvent = new OptaMatchEvent();
        int competitionId = myF1.containsKey("competition_id")? (int) myF1.get("competition_id"): -1;
        int seasonId = myF1.containsKey("season_id")? (int) myF1.get("season_id"): -1;
        String seasonName = myF1.containsKey("season_name")? (String) myF1.get("season_name"): "NO SEASON NAME";
        String competitionName = myF1.containsKey("competition_name")?
                (String) myF1.get("competition_name"): "NO COMPETITION NAME";
        LinkedHashMap matchInfo = (LinkedHashMap) matchObject.get("MatchInfo");
        myOptaMatchEvent.id = (String) matchObject.get("uID");
        myOptaMatchEvent.lastModified = parseDate((String) matchObject.get("last_modified"));
        myOptaMatchEvent.matchDate = parseDate((String) matchInfo.get("Date"));
        myOptaMatchEvent.competitionId = competitionId;
        myOptaMatchEvent.seasonId = seasonId;
        myOptaMatchEvent.seasonName = seasonName;
        myOptaMatchEvent.competitionName = competitionName;
        myOptaMatchEvent.timeZone = (String) matchInfo.get("TZ");
        ArrayList teams = matchObject.containsKey("TeamData")? (ArrayList)matchObject.get("TeamData"): null;
        if (teams != null)
            for (Object team: teams){
                if (((LinkedHashMap)team).get("Side").equals("Home")) {
                    myOptaMatchEvent.homeTeamId = (String) ((LinkedHashMap)team).get("TeamRef");
                } else {
                    myOptaMatchEvent.awayTeamId = (String) ((LinkedHashMap)team).get("TeamRef");
                }
            }
        Model.optaMatchEvents().update("{id: #}", myOptaMatchEvent.id).upsert().with(myOptaMatchEvent);
    }

    private static void processMatchData(LinkedHashMap matchObject, int competitionId, LinkedHashMap myF1) {
        HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);
        String matchId = (String) matchObject.get("uID");
        Date timestamp = parseDate((String) matchObject.get("last_modified"));
        if (optaMatchDatas.containsKey(matchId)) {
            if (timestamp.after(optaMatchDatas.get(matchId))) {
                updateOrInsertMatchData(myF1, matchObject);
                //updateOrInsertMatchData(matchObject, competitionId);
                optaMatchDatas.put(matchId, timestamp);
            }
        } else {
            updateOrInsertMatchData(myF1, matchObject);
            //updateOrInsertMatchData(matchObject, competitionId);
            optaMatchDatas.put(matchId, timestamp);
        }
    }

    public static void processF9(BasicDBObject f9){
        ArrayList teams = new ArrayList();
        try {
            LinkedHashMap myF9 = (LinkedHashMap) f9.get("SoccerFeed");
            myF9 = (LinkedHashMap) myF9.get("SoccerDocument");
            if (myF9.get("Type").equals("Result")){
                processFinishedMatch(myF9);
            }

            if (myF9.containsKey("Team")) {
                teams = (ArrayList) myF9.get("Team");
            } else {
                if (myF9.containsKey("Match")) { //TODO: Aserciones
                    LinkedHashMap match = (LinkedHashMap) (myF9.get("Match"));
                    teams = (ArrayList) match.get("Team");
                } else {
                    System.out.println("no match");
                }
            }

            for (Object team : teams) {
                LinkedHashMap teamObject = (LinkedHashMap) team;
                ArrayList playersList = (ArrayList) teamObject.get("Player");
                OptaTeam myTeam = new OptaTeam();
                myTeam.id = (String) teamObject.get("uID");
                myTeam.name = (String) teamObject.get("Name");
                myTeam.shortName = (String) teamObject.get("SYMID");
                myTeam.updatedTime = System.currentTimeMillis();
                if (playersList != null) { //Si no es un equipo placeholder
                    Model.optaTeams().update("{id: #}", myTeam.id).upsert().with(myTeam);
                    for (Object player : playersList) {
                        LinkedHashMap playerObject = (LinkedHashMap) player;
                        String playerId = (String) playerObject.get("uID");
                        // First search if player already exists:
                        if (playerId != null) {
                            OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                            Model.optaPlayers().update("{id: #}", playerId).upsert().with(myPlayer);
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }


    public static void processFinishedMatch(LinkedHashMap F9){
        String gameId = (String) F9.get("uID");
        ArrayList<LinkedHashMap> teamDatas =  (ArrayList<LinkedHashMap>)((LinkedHashMap)F9.get("MatchData")).get("TeamData");
        for (LinkedHashMap teamData: teamDatas){
            for (LinkedHashMap teamStat: (ArrayList<LinkedHashMap>) teamData.get("Stat")){
                if (teamStat.get("Type").equals("goals_conceded")) {
                    if ((int) teamStat.get("content") == 0) {
                        processCleanSheet(F9, gameId, teamData);
                    }else {
                        processGoalsAgainst(F9, gameId, teamData);
                    }
                }
            }
        }
        registerChange(gameId);
    }

    public static void processGoalsAgainst(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {
        ArrayList<LinkedHashMap> matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).
                                                                                      get("MatchPlayer");
        for (LinkedHashMap matchPlayer : matchPlayers) {
            if (matchPlayer.get("Position").equals("Goalkeeper") || matchPlayer.get("Position").equals("Defender")) {
                for (LinkedHashMap stat : (ArrayList<LinkedHashMap>) matchPlayer.get("Stat")) {
                    if (stat.get("Type").equals("goals_conceded") && ((int) stat.get("content") > 0)) {
                        createEvent(F9, gameId, matchPlayer, 2001, 20001, (int) stat.get("content"));
                    }
                }
            }
        }
    }

    public static void processCleanSheet(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {
        ArrayList<LinkedHashMap> matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).
                                                                                      get("MatchPlayer");
        for (LinkedHashMap matchPlayer : matchPlayers) {
            if (matchPlayer.get("Position").equals("Goalkeeper") || matchPlayer.get("Position").equals("Defender")) {
                for (LinkedHashMap stat : (ArrayList<LinkedHashMap>) matchPlayer.get("Stat")) {
                    if (stat.get("Type").equals("mins_played") && ((int) stat.get("content") > 59)) {
                        createEvent(F9, gameId, matchPlayer, 2000, 20000, 1);
                    }
                }
            }
        }
    }

    public static void createEvent(LinkedHashMap F9, String gameId, LinkedHashMap matchPlayer,
                                   int typeId, int eventId, int times) {
        String playerId = (String) matchPlayer.get("PlayerRef");
        playerId = playerId.startsWith("p")? playerId.substring(1): playerId;
        String competitionId = (String)((LinkedHashMap)F9.get("Competition")).get("uID");
        competitionId = competitionId.startsWith("c")? competitionId.substring(1): competitionId;
        gameId = gameId.startsWith("f")? gameId.substring(1): gameId;
        Date timestamp = parseDate((String) ((LinkedHashMap) ((LinkedHashMap) F9.get("MatchData")).get("MatchInfo")).get("TimeStamp"));
        long unixtimestamp = timestamp.getTime();

        Model.optaEvents().remove("{typeId: #, eventId: #, optaPlayerId: #, gameId: #, competitionId: #}",
                typeId, eventId, playerId, gameId, competitionId);


        OptaEvent[] events = new OptaEvent[times];
        OptaEvent myEvent;
        for (int i = 0; i < times; i++) {
            myEvent = new OptaEvent();
            myEvent.typeId = typeId;
            myEvent.eventId = eventId;
            myEvent.optaPlayerId = playerId;
            myEvent.gameId = gameId;
            myEvent.competitionId = competitionId;
            //TODO: Extraer SeasonID de Competition->Stat->Type==season_id->content
            myEvent.timestamp = timestamp;
            myEvent.unixtimestamp = unixtimestamp;
            myEvent.qualifiers = new ArrayList<>();
            myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
            myEvent.pointsTranslationId = pointsTranslationTableCache.get(myEvent.typeId);

            events[i] = myEvent;
        }
        Model.optaEvents().insert((Object[]) events);
    }

    public static OptaPlayer createPlayer(LinkedHashMap playerObject, LinkedHashMap teamObject){
        OptaPlayer myPlayer = new OptaPlayer();

        if (playerObject.containsKey("firstname")){
            myPlayer.id = (String) playerObject.get("id");
            myPlayer.firstname = (String) playerObject.get("firstname");
            myPlayer.lastname = (String) playerObject.get("lastname");
            myPlayer.position = (String) playerObject.get("position");
            myPlayer.teamId = (String) teamObject.get("id");
            myPlayer.teamName = (String) teamObject.get("name");
        }else if (playerObject.containsKey("Name")){
            myPlayer.id = (String) playerObject.get("uID");
            myPlayer.name = (String) playerObject.get("Name");
            myPlayer.position = (String) playerObject.get("Position");
            myPlayer.teamId = (String) teamObject.get("uID");
            myPlayer.teamName = (String) teamObject.get("Name");
        }
        myPlayer.updatedTime = System.currentTimeMillis();
        return myPlayer;
    }

    private static void resetPointsTranslationCache() {
        pointsTranslationCache = new HashMap<Integer, Integer>();
        pointsTranslationTableCache = new HashMap<Integer, ObjectId>();
    }

    private static HashMap getOptaEventsCache(String key) {
        if (optaEventsCache == null){
            optaEventsCache = new HashMap<String, HashMap>();
        }
        if (!optaEventsCache.containsKey(key)){
            optaEventsCache.put(key, new HashMap<Integer, Date>());
        }
        return optaEventsCache.get(key);
    }

    private static HashMap getOptaMatchDataCache(int key) {
        if (optaMatchDataCache == null){
            optaMatchDataCache = new HashMap<Integer, HashMap>();
        }
        if (!optaMatchDataCache.containsKey(key)){
            optaMatchDataCache.put(key, new HashMap<String, Date>());
        }
        return optaMatchDataCache.get(key);
    }

    private static void resetChanges() {
        dirtyMatchEvents = new HashSet<>();
    }

    private static void registerChange(String gameId) {
        dirtyMatchEvents.add(gameId);
    }

    private static void applyChanges() {
        if (dirtyMatchEvents.isEmpty())
            return;

        for(String optaGameId : dirtyMatchEvents) {
            //Logger.info("optaGameId in gameId({})", optaGameId);

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            Iterable<TemplateMatchEvent> templateMatchEvents = Model.templateMatchEvents().find("{optaMatchEventId : #}", "g" + optaGameId).as(TemplateMatchEvent.class);
            while(templateMatchEvents.iterator().hasNext()) {
                TemplateMatchEvent templateMatchEvent = templateMatchEvents.iterator().next();

                // Existe la version "live" del match event?
                LiveMatchEvent liveMatchEvent = Model.liveMatchEvent(templateMatchEvent);
                if (liveMatchEvent == null) {
                    // Deberia existir? (true si el partido ha comenzado)
                    if (Model.isMatchEventStarted(templateMatchEvent)) {
                        liveMatchEvent = Model.createLiveMatchEvent(templateMatchEvent);
                    }
                }

                if (liveMatchEvent != null) {
                    Model.updateLiveFantasyPoints(liveMatchEvent);

                    //Logger.info("fantasyPoints in liveMatchEvent({})", liveMatchEvent.liveMatchEventId);

                    if (Model.isMatchEventFinished(templateMatchEvent)) {
                        Model.actionWhenMatchEventIsFinished(templateMatchEvent);
                    }
                    else {
                        Model.actionWhenMatchEventIsStarted(templateMatchEvent);
                    }
                }

                //Logger.info("optaGameId in templateMatchEvent({})", templateMatchEvent.templateMatchEventId);
            }
        }
    }

    private static HashSet<String> dirtyMatchEvents;

    private static HashMap<Integer, Integer> pointsTranslationCache;
    private static HashMap<Integer, ObjectId> pointsTranslationTableCache;

    private static HashMap<String, HashMap> optaEventsCache;

    private static HashMap<Integer, HashMap> optaMatchDataCache;

}
