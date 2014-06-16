package utils;

import com.mongodb.BasicDBObject;
import com.mongodb.WriteResult;
import model.Model;
import model.PointsTranslation;
import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaUtils {

    public static void processOptaDBInput(String feedType, BasicDBObject requestBody){
        if (feedType.equals("F9")){
            processF9(requestBody);
        }
        else if (feedType.equals("F24")){
            processEvents(requestBody);
        }
        else if (feedType.equals("F1")){
            processF1(requestBody);
        }

    }

    public static void processEvents(BasicDBObject gamesObj){
        try {
            LinkedHashMap games = (LinkedHashMap) gamesObj.get("Games");
            LinkedHashMap game = (LinkedHashMap) games.get("Game");

            Object events = game.get("Event");
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

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent._id = new ObjectId();
        myEvent.gameId = game.get("id").toString();
        myEvent.homeTeamId = game.get("home_team_id").toString();
        myEvent.awayTeamId = game.get("away_team_id").toString();
        myEvent.competitionId = game.get("competition_id").toString();
        myEvent.seasonId = game.get("season_id").toString();
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
        // Falta inflingida -> 1004
        if (myEvent.typeId==4 && myEvent.outcome==0){
            myEvent.typeId = 1004;
        }
        // Tarjeta roja -> 1017
        if (myEvent.typeId==17 && myEvent.qualifiers.contains(33)){
            myEvent.typeId = 1017;
        }
        /*
        // Gol al portero -> 1999
        if (my_event.type_id==16){
            //TODO: Extract current opposite goalkeeper
            derivateEvent(1999, goalkeeper, my_event);
        }
        */

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}",
                        myEvent.typeId, myEvent.timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            myEvent.pointsTranslationId = pointsTranslation._id;
            myEvent.points = pointsTranslation.points;
        }


        Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).upsert().with(myEvent);
    }

    private static Date parseDate(String timestamp) {
        String dateConfig = timestamp.indexOf('T')>0? "yyyy-MM-dd'T'hh:mm:ss.SSSz" : "yyyy-MM-dd hh:mm:ss.SSSz";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
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
            int seasonId = myF1.containsKey("season_id")? (int) myF1.get("season_id"): -1;
            String seasonName = myF1.containsKey("season_name")? (String) myF1.get("season_name"): "NO SEASON NAME";
            String competitionName = myF1.containsKey("competition_name")?
                    (String) myF1.get("competition_name"): "NO COMPETITION NAME";


            ArrayList matches = myF1.containsKey("MatchData")? (ArrayList) myF1.get("MatchData"): null;
            if (matches != null){
                for (Object match: matches){
                    OptaMatchEvent myOptaMatchEvent = new OptaMatchEvent();

                    LinkedHashMap matchObject = (LinkedHashMap)match;
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

            }
        }catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    public static void processF9(BasicDBObject f9){
        ArrayList teams = new ArrayList();
        try {
            LinkedHashMap myF9 = (LinkedHashMap) f9.get("SoccerFeed");
            myF9 = (LinkedHashMap) myF9.get("SoccerDocument");
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
                    Model.optaTeams().update("{id: #}", myTeam.id).upsert().with(myTeam); //TODO: meter upsert antes de with

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
}
