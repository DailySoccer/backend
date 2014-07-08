package model.opta;

import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import play.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaProcessor {

    public HashSet<String> processOptaDBInput(String feedType, String requestBody) {
        SAXBuilder builder = new SAXBuilder();
        Document document = null;
        try {
            document = (Document) builder.build(new StringReader(requestBody));
        } catch (JDOMException e) {
            Logger.error("WTF 956302513", e);
        } catch (IOException e) {
            Logger.error("WTF 556261163", e);
        }
        Element rootNode = document.getRootElement();
        return processOptaDBInput(feedType, rootNode);
    }

    // Retorna los Ids de opta (gameIds, optaMachEventId) de los partidos que han cambiado
    public HashSet<String> processOptaDBInput(String feedType, Element requestBody) {
        _dirtyMatchEvents = new HashSet<>();

        if (feedType != null) {
            if (feedType.equals("F9")) {
                processF9(requestBody);
            } else if (feedType.equals("F24")) {
                processEvents(requestBody);
            } else if (feedType.equals("F1")) {
                processF1(requestBody);
            }
        }

        return _dirtyMatchEvents;
    }

    private void processEvents(Element gamesObj) {
        try {
            resetPointsTranslationCache();

            Element game = gamesObj.getChild("Game");
            List<Element> events = game.getChildren("Event");
            for (Element event : events) {
                processEvent(event, game);
            }
        } catch (NullPointerException e){
            Logger.error("WTF 615402197", e);
        }
    }

    private void processEvent(Element event, Element game) {
        Date timestamp = (event.getAttribute("last_modified") != null) ?
                             OptaEvent.parseDate(event.getAttributeValue("last_modified")):
                             OptaEvent.parseDate(event.getAttributeValue("timestamp"));

        HashMap<Integer, Date> eventsCache = getOptaEventsCache(game.getAttributeValue("id"));
        int eventId = (int) Integer.parseInt(event.getAttributeValue("event_id"));

        if (!eventsCache.containsKey(eventId) || timestamp.after(eventsCache.get(eventId))) {
            updateOrInsertEvent(event, game);
            eventsCache.put(eventId, timestamp);
        }
    }

    private void updateOrInsertEvent(Element event, Element game) {
        OptaEvent myEvent = new OptaEvent(event, game);
        myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
        myEvent.pointsTranslationId = _pointsTranslationTableCache.get(myEvent.typeId);

        Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).upsert().with(myEvent);
        _dirtyMatchEvents.add(myEvent.gameId);
    }


    public void recalculateAllEvents() {
        resetPointsTranslationCache();

        for (OptaEvent event : Model.optaEvents().find().as(OptaEvent.class)) {
            recalculateEvent(event);
        }
    }

    public int getPoints(int typeId, Date timestamp) {
        if (!_pointsTranslationCache.containsKey(typeId)) {
            getPointsTranslation(typeId, timestamp);
        }
        return _pointsTranslationCache.get(typeId);
    }

    private void recalculateEvent(OptaEvent optaEvent) {
        optaEvent.points = getPoints(optaEvent.typeId, optaEvent.timestamp);
        optaEvent.pointsTranslationId = _pointsTranslationTableCache.get(optaEvent.typeId);
        Model.optaEvents().update("{eventId: #, gameId: #}", optaEvent.eventId, optaEvent.gameId).upsert().with(optaEvent);
    }


    private PointsTranslation getPointsTranslation(int typeId, Date timestamp){
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            _pointsTranslationCache.put(typeId, pointsTranslation.points);
            _pointsTranslationTableCache.put(typeId, pointsTranslation.pointsTranslationId);
        } else {
            _pointsTranslationCache.put(typeId, 0);
            _pointsTranslationTableCache.put(typeId, null);
        }
        return pointsTranslation;
    }

    private void processF1(Element f1) {
        try {
            Element myF1 = f1.getChild("SoccerDocument");
            List<Element> matches = myF1.getChildren("MatchData");

            if (matches != null) {
                int competitionId = (myF1.getAttribute("competition_id")!=null)?
                                        (int) Integer.parseInt(myF1.getAttributeValue("competition_id")) : -1;

                for (Element match : matches) {
                    processMatchData(match, competitionId, myF1);
                }
            }
        } catch (NullPointerException e){
            Logger.error("WTF 253102754432", e);
        }
    }

    private void processMatchData(Element matchObject, int competitionId, Element myF1) {

        HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);
        String matchId = matchObject.getAttributeValue("uID");
        Date timestamp = OptaEvent.parseDate(matchObject.getAttributeValue("last_modified"));

        if (!optaMatchDatas.containsKey(matchId) || timestamp.after(optaMatchDatas.get(matchId))) {
            updateOrInsertMatchData(myF1, matchObject);
            optaMatchDatas.put(matchId, timestamp);
        }
    }

    private void updateOrInsertMatchData(Element myF1, Element matchObject) {
        Element matchInfo = matchObject.getChild("MatchInfo");

        OptaMatchEvent optaMatchEvent = new OptaMatchEvent();
        optaMatchEvent.optaMatchEventId = matchObject.getAttributeValue("uID");
        if (matchObject.getAttribute("last_modified") != null) {
            optaMatchEvent.lastModified = OptaEvent.parseDate(matchObject.getAttributeValue("last_modified"));
        }
        optaMatchEvent.matchDate = OptaEvent.parseDate(matchInfo.getChild("Date").getContent().get(0).getValue());
        optaMatchEvent.competitionId = getStringValue(myF1, "competition_id", "NO COMPETITION ID");
        optaMatchEvent.seasonId = getStringValue(myF1, "season_id", "NO SEASON ID");

        optaMatchEvent.seasonName = (myF1.getAttribute("season_name")!=null)? myF1.getAttributeValue("season_name"): "NO SEASON NAME";
        optaMatchEvent.competitionName = (myF1.getAttribute("competition_name")!=null)? myF1.getAttributeValue("competition_name"): "NO COMPETITION NAME";
        optaMatchEvent.timeZone = matchInfo.getChild("TZ").getContent().get(0).getValue();

        List<Element> teams = matchObject.getChildren("TeamData");
        if (teams != null) {
            for (Element team : teams) {
                if (team.getAttributeValue("Side").equals("Home")) {
                    optaMatchEvent.homeTeamId = team.getAttributeValue("TeamRef");
                } else {
                    optaMatchEvent.awayTeamId =  team.getAttributeValue("TeamRef");
                }
            }
        }
        Model.optaMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(optaMatchEvent);
    }

    private String getStringValue(Element document, String key, String defaultValue){
        return (document.getAttribute(key)!=null)? document.getAttributeValue(key) : defaultValue ;
    }

    private void processF9(Element f9) {
        if (null == _pointsTranslationCache) {
            resetPointsTranslationCache();
        }
        try {
            Element myF9 = f9.getChild("SoccerDocument");
                if (myF9.getAttribute("Type").getValue().equals("Result")) {
                    processFinishedMatch(myF9);
                } else if (myF9.getAttribute("Type").getValue().equals("STANDINGS Latest") ||
                           myF9.getAttribute("Type").getValue().equals("SQUADS Latest") ) {
                    List<Element> teams = getTeamsFromF9(myF9);

                    for (Element team : teams) {
                        OptaTeam myTeam = new OptaTeam();
                        myTeam.optaTeamId = team.getAttributeValue("uID");
                        myTeam.name = team.getChild("Name").getContent().get(0).getValue();// AttributeValue("Name");
                        if (null != team.getChild("SYMID") && team.getChild("SYMID").getContentSize()>0) {
                            myTeam.shortName = team.getChild("SYMID").getContent().get(0).getValue();//getAttributeValue("SYMID");
                        }
                        myTeam.updatedTime = System.currentTimeMillis();

                        List<Element> playersList = team.getChildren("Player");
                        if (playersList != null) { // Si no es un equipo placeholder
                            Model.optaTeams().update("{optaTeamId: #}", myTeam.optaTeamId).upsert().with(myTeam);

                            for (Element player : playersList) {
                                String playerId = player.getAttributeValue("uID");

                                // First search if player already exists:
                                if (playerId != null) { // && !playerObject.containsKey("PersonName")) {
                                    OptaPlayer myPlayer = createPlayer(player, team);
                                    if (myPlayer != null) {
                                        Model.optaPlayers().update("{optaPlayerId: #}", playerId).upsert().with(myPlayer);
                                    }
                                }
                            }
                        }

                    }
                }

        } catch (NullPointerException e) {
            Logger.error("WTF 9930256213", e);
        }


    }

    private OptaPlayer createPlayer(Element playerObject, Element teamObject) {
        OptaPlayer myPlayer = new OptaPlayer();
            if (playerObject.getAttribute("firstname") != null){
                myPlayer.optaPlayerId = playerObject.getAttributeValue("id");
                myPlayer.firstname = playerObject.getAttributeValue("firstname");
                myPlayer.lastname = playerObject.getAttributeValue("lastname");
                myPlayer.name = myPlayer.firstname+" "+myPlayer.lastname;
                myPlayer.position = playerObject.getAttributeValue("position");
                myPlayer.teamId = teamObject.getAttributeValue("id");
                myPlayer.teamName = teamObject.getAttributeValue("name");
            }
            else {
                if (playerObject.getAttribute("uID") != null){
                    myPlayer.optaPlayerId = playerObject.getAttributeValue("uID");
                }
                if (playerObject.getChild("Name") != null) {
                    myPlayer.name = playerObject.getChild("Name").getContent().get(0).getValue();
                    myPlayer.optaPlayerId = playerObject.getAttributeValue("uID");
                    myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
                } else if (playerObject.getChild("PersonName") != null){
                    if (playerObject.getChild("PersonName").getChild("Known") != null) {
                        myPlayer.nickname = playerObject.getChild("PersonName").getChild("Known").getContent().get(0).getValue();
                    }
                    myPlayer.firstname = playerObject.getChild("PersonName").getChild("First").getContent().get(0).getValue();
                    myPlayer.lastname = playerObject.getChild("PersonName").getChild("Last").getContent().get(0).getValue();
                    myPlayer.name = myPlayer.firstname+" "+myPlayer.lastname;
                } else {
                    Logger.error("Not getting name for: "+myPlayer.optaPlayerId);
                }
                if (playerObject.getChild("Position") != null){
                    myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
                    if (myPlayer.position.equals("Substitute")) {
                        Logger.error("WTF 233442: Sustituto! {}", myPlayer.name );
                    }
                }
                myPlayer.teamId = teamObject.getAttributeValue("uID");
                myPlayer.teamName = teamObject.getChild("Name").getContent().get(0).getValue();
            }
            myPlayer.updatedTime = System.currentTimeMillis();
            return myPlayer;
    }


    private List<Element> getTeamsFromF9(Element myF9) {
        List<Element> teams = new ArrayList();

        if (null != myF9.getChild("Team")) {
            teams = myF9.getChildren("Team");
        } else {
            if (null != myF9.getChild("Match")) {
                teams = myF9.getChild("Match").getChildren("Team");
            } else {
                Logger.error("WTF 34825t294: No match");
            }
        }
        return teams;
    }


    private void processFinishedMatch(Element F9) {
        String gameId = F9.getAttribute("uID").getValue();

        List<Element> teamDatas = F9.getChild("MatchData").getChildren("TeamData");

        for (Element teamData : teamDatas) {
            List<Element> teamStats = teamData.getChildren("Stat");

            for (Element teamStat : teamStats) {
                if (teamStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                    if ((int) Integer.parseInt(teamStat.getContent().get(0).getValue()) == 0) {
                        processCleanSheet(F9, gameId, teamData);
                    } else {
                        processGoalsAgainst(F9, gameId, teamData);
                    }
                }
            }
        }

        _dirtyMatchEvents.add(gameId);
    }


    private void processGoalsAgainst(Element F9, String gameId, Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getAttribute("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getAttribute("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("goals_conceded") &&
                        ((int) Integer.parseInt(stat.getContent().get(0).getValue()) > 0)) {
                        createEvent(F9, gameId, matchPlayer, 2001, 20001,
                                    (int) Integer.parseInt(stat.getContent().get(0).getValue()));
                    }
                }
            }
        }
    }


    private void processCleanSheet(Element F9, String gameId, Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getChild("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getChild("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("mins_played") &&
                        ((int) Integer.parseInt(stat.getContent().get(0).getValue()) > 59)) {
                        createEvent(F9, gameId, matchPlayer, 2000, 20000, 1);
                    }
                }
            }
        }

    }

    private void createEvent(Element F9, String gameId, Element matchPlayer, int typeId, int eventId, int times) {
        String playerId = matchPlayer.getAttribute("PlayerRef").getValue();
        playerId = playerId.startsWith("p")? playerId.substring(1): playerId;

        String competitionId = F9.getChild("Competition").getAttributeValue("uID");
        competitionId = competitionId.startsWith("c")? competitionId.substring(1): competitionId;

        gameId = gameId.startsWith("f")? gameId.substring(1): gameId;

        Date timestamp = OptaEvent.parseDate(F9.getChild("MatchData").getChild("MatchInfo").getAttributeValue("TimeStamp"));
        //long unixtimestamp = timestamp.getTime();

        Model.optaEvents().remove("{typeId: #, eventId: #, optaPlayerId: #, gameId: #, competitionId: #}",
                typeId, eventId, playerId, gameId, competitionId);

        OptaEvent[] events = new OptaEvent[times];

        for (int i = 0; i < times; i++) {
            OptaEvent myEvent = new OptaEvent();
            myEvent.typeId = typeId;
            myEvent.eventId = eventId;
            myEvent.optaPlayerId = playerId;
            myEvent.gameId = gameId;
            myEvent.competitionId = competitionId;
            //TODO: Extraer SeasonID de Competition->Stat->Type==season_id->content
            myEvent.timestamp = timestamp;
            //myEvent.unixtimestamp = unixtimestamp;
            myEvent.qualifiers = new ArrayList<>();
            myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
            myEvent.pointsTranslationId = _pointsTranslationTableCache.get(myEvent.typeId);

            events[i] = myEvent;
        }
        Model.optaEvents().insert((Object[]) events);
    }

    private void resetPointsTranslationCache() {
        _pointsTranslationCache = new HashMap<Integer, Integer>();
        _pointsTranslationTableCache = new HashMap<Integer, ObjectId>();
    }

    private HashMap<Integer, Date> getOptaEventsCache(String gameId) {
        if (_optaEventsCache == null) {
            _optaEventsCache = new HashMap<String, HashMap<Integer, Date>>();
        }
        if (!_optaEventsCache.containsKey(gameId)) {
            _optaEventsCache.put(gameId, new HashMap<Integer, Date>());
        }
        return _optaEventsCache.get(gameId);
    }

    private HashMap<String, Date> getOptaMatchDataCache(int competitionId) {
        if (_optaMatchDataCache == null) {
            _optaMatchDataCache = new HashMap<Integer, HashMap<String, Date>>();
        }
        if (!_optaMatchDataCache.containsKey(competitionId)) {
            _optaMatchDataCache.put(competitionId, new HashMap<String, Date>());
        }
        return _optaMatchDataCache.get(competitionId);
    }

    private HashSet<String> _dirtyMatchEvents;

    private HashMap<Integer, Integer> _pointsTranslationCache;
    private HashMap<Integer, ObjectId> _pointsTranslationTableCache;

    private HashMap<String, HashMap<Integer, Date>> _optaEventsCache;
    private HashMap<Integer, HashMap<String, Date>> _optaMatchDataCache;

}