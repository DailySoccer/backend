package model;


import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaPlayer;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import play.Play;
import utils.ListUtils;

import java.util.*;

public class TemplateSoccerPlayer implements JongoId {
    @Id
    public ObjectId templateSoccerPlayerId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    public ObjectId templateTeamId;

    public List<TemplateSoccerPlayerTag> tags;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.Extended.class)
    public List<SoccerPlayerStats> stats = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public int getPlayedMatches() {
        int numPlayed = 0;
        for (SoccerPlayerStats stat: stats) {
            if (stat.hasPlayed()) {
                numPlayed++;
            }
        }
        return numPlayed;
    }

    public TemplateSoccerPlayer() { }

    public TemplateSoccerPlayer(OptaPlayer optaPlayer, ObjectId aTemplateTeamId) {
        optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = transformToFieldPosFromOptaPos(optaPlayer.position);
        templateTeamId = aTemplateTeamId;
        createdAt = GlobalDate.getCurrentDate();
        tags = new ArrayList<>();
        if (Play.application().configuration().getBoolean("activate_players_by_default")) {
            tags.add(TemplateSoccerPlayerTag.ACTIVO);
        }
    }

    public ObjectId getId() {
        return templateSoccerPlayerId;
    }

    static FieldPos transformToFieldPosFromOptaPos(String optaPosition) {
        FieldPos optaFieldPos = FieldPos.FORWARD;

        if      (optaPosition.startsWith("G"))  optaFieldPos = FieldPos.GOALKEEPER;
        else if (optaPosition.startsWith("D"))  optaFieldPos = FieldPos.DEFENSE;
        else if (optaPosition.startsWith("M"))  optaFieldPos = FieldPos.MIDDLE;
        else if (optaPosition.startsWith("F"))  optaFieldPos = FieldPos.FORWARD;
        else {
            Logger.error("Opta Position not registered yet: {}", optaPosition);
        }
        return optaFieldPos;
    }

    static public TemplateSoccerPlayer findOne(ObjectId templateSoccerPlayerId) {
        return Model.templateSoccerPlayers().findOne("{_id : #}", templateSoccerPlayerId).as(TemplateSoccerPlayer.class);
    }

    static public TemplateSoccerPlayer findOneFromOptaId(String optaPlayerId) {
        return Model.templateSoccerPlayers().findOne("{optaPlayerId: #}", optaPlayerId).as(TemplateSoccerPlayer.class);
    }

    public static List<TemplateSoccerPlayer> findAll() {
        return ListUtils.asList(Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class));
    }

    public static List<TemplateSoccerPlayer> findAll(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.templateSoccerPlayers(), "_id", idList).as(TemplateSoccerPlayer.class));
    }

    static public HashMap<String, TemplateSoccerPlayer> findAllAsMap(){
        HashMap<String, TemplateSoccerPlayer> map = new HashMap<>();
        for (TemplateSoccerPlayer optaPlayer: findAll()) {
            map.put(optaPlayer.optaPlayerId, optaPlayer);
        }
        return map;
    }

    static public List<TemplateSoccerPlayer> findAllFromTemplateTeam(ObjectId templateSoccerTeamId) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }

    static public List<TemplateSoccerPlayer> findAllActiveFromTemplateTeam(ObjectId templateSoccerTeamId) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: #, tags: {$elemMatch: {$eq: #}} }", templateSoccerTeamId, TemplateSoccerPlayerTag.ACTIVO).as(TemplateSoccerPlayer.class));
    }

    static public List<TemplateSoccerPlayer> findAllFromInstances(List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        List<ObjectId> templateSoccerPlayerIds = new ArrayList<>();
        for (InstanceSoccerPlayer instanceSoccerPlayer: instanceSoccerPlayers) {
            templateSoccerPlayerIds.add(instanceSoccerPlayer.templateSoccerPlayerId);
        }
        return findAll(templateSoccerPlayerIds);
    }

    static public List<TemplateSoccerPlayer> findAllActiveFromTeams(List<TemplateSoccerTeam> templateSoccerTeams) {
        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateSoccerTeam team: templateSoccerTeams) {
            teamIds.add(team.templateSoccerTeamId);
        }
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: {$in: #}, tags: {$elemMatch: {$eq: #}} }", teamIds, TemplateSoccerPlayerTag.ACTIVO).as(TemplateSoccerPlayer.class));
    }

    public void updateStats(SoccerPlayerStats soccerPlayerStats) {
        boolean updateStats = true;

        // Buscar si ya tenemos estadísticas de ese mismo partido
        int index = searchIndexForMatchEvent(soccerPlayerStats.optaMatchEventId);
        // Son estadísticas nuevas?
        if (index == -1) {
            // Añadimos una nueva estadística
            stats.add(soccerPlayerStats);
        }
        else {
            // Actualizar las estadísticas
            stats.set(index, soccerPlayerStats);
        }

        fantasyPoints = calculateFantasyPointsFromStats();

        if (index == -1) {
            Model.templateSoccerPlayers()
                    .update("{optaPlayerId: #}", soccerPlayerStats.optaPlayerId)
                    .with("{$set: {fantasyPoints: #}, $push: {stats: #}}", fantasyPoints, soccerPlayerStats);
        }
        else {
            Model.templateSoccerPlayers()
                    .update("{optaPlayerId: #, \"stats.optaMatchEventId\":#}", soccerPlayerStats.optaPlayerId, soccerPlayerStats.optaMatchEventId)
                    .with("{$set: {fantasyPoints: #, \"stats.$\": #}}", fantasyPoints, soccerPlayerStats);
        }
    }

    private int calculateFantasyPointsFromStats() {
        int numPlayedMatches = 0;
        int fantasyPointsMedia = 0;
        for (SoccerPlayerStats stat : stats) {
            if (stat.hasPlayed()) {
                fantasyPointsMedia += stat.fantasyPoints;
                numPlayedMatches++;
            }
        }
        if (numPlayedMatches > 0) {
            fantasyPointsMedia /= numPlayedMatches;
        }
        return fantasyPointsMedia;
    }

    private int searchIndexForMatchEvent(String optaMatchEventId) {
        int index = -1;
        for (int i=0; i<stats.size(); i++) {
            SoccerPlayerStats stat = stats.get(i);
            if (stat.optaMatchEventId.equals(optaMatchEventId)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public boolean hasChanged(OptaPlayer optaPlayer) {
        return !optaPlayerId.equals(optaPlayer.optaPlayerId) ||
               !name.equals(optaPlayer.name) ||
               !fieldPos.equals(transformToFieldPosFromOptaPos(optaPlayer.position)) ||
               (TemplateSoccerTeam.findOne(templateTeamId, optaPlayer.teamId) == null);
    }

    public void changeDocument(OptaPlayer optaPlayer) {
        // optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = transformToFieldPosFromOptaPos(optaPlayer.position);

        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);
        if (templateSoccerTeam != null) {
            templateTeamId = templateSoccerTeam.templateSoccerTeamId;
            updateDocument();
        }
        else {
            Logger.error("WTF 8791: TeamID({}) inválido", optaPlayer.teamId);
        }
    }

    public void updateDocument() {
        Model.templateSoccerPlayers().withWriteConcern(WriteConcern.SAFE).update("{optaPlayerId: #}", optaPlayerId).upsert().with(this);
    }

    /**
     * Importar un optaPlayer
     */
    static public boolean importSoccer(OptaPlayer optaPlayer) {
        TemplateSoccerTeam templateTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);


        if (templateTeam != null) {
            TemplateSoccerPlayer templateSoccer = new TemplateSoccerPlayer(optaPlayer, templateTeam.templateSoccerTeamId);

            TemplateSoccerPlayer origTemplateSoccerPlayer = TemplateSoccerPlayer.findOneFromOptaId(optaPlayer.optaPlayerId);
            templateSoccer.salary = (origTemplateSoccerPlayer != null)? origTemplateSoccerPlayer.salary:
                                                                        Play.application().configuration().getInt("base_salary")>0?
                                                                                Play.application().configuration().getInt("base_salary"):
                                                                                templateSoccer.name.length()*500;

            templateSoccer.updateDocument();
        }
        else {
            Logger.error("importSoccer ({}): invalid teamID({})", optaPlayer.optaPlayerId, optaPlayer.teamId);
            return false;
        }
        return true;
    }

    static public boolean isInvalidFromImport(OptaPlayer optaPlayer) {
        boolean invalid = (optaPlayer.teamId == null) || optaPlayer.teamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam templateTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);
            invalid = (templateTeam == null);
        }

        return invalid;
    }

}
