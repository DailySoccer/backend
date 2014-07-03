package model;

import org.bson.types.ObjectId;
import java.util.ArrayList;

public class SoccerTeam {
    public ObjectId templateSoccerTeamId;
    public String optaTeamId;
    public String name;
    public String shortName;
    public ArrayList<SoccerPlayer> soccerPlayers = new ArrayList<>();

    public int getFantasyPoints() {
        int totalFantasyPoints = 0;
        for (SoccerPlayer player: soccerPlayers) {
            totalFantasyPoints += player.fantasyPoints;
        }
        return totalFantasyPoints;
    }

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerTeam() {
    }

    public SoccerTeam(TemplateSoccerTeam template) {
        templateSoccerTeamId = template.templateSoccerTeamId;
        optaTeamId = template.optaTeamId;
        name = template.name;
        shortName = template.shortName;
    }

    /**
     * Setup Team (incrustando a los futbolistas en el equipo)
     * @param templateTeam
     * @return
     */
    public static SoccerTeam create(TemplateMatchEvent templateMatchEvent, TemplateSoccerTeam templateTeam) {
        SoccerTeam team = new SoccerTeam(templateTeam);

        Iterable<TemplateSoccerPlayer> playersTeamA = Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateTeam.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamA) {
            SoccerPlayer player = new SoccerPlayer(templateSoccer);

            // Calcular el numero de partidos jugados en la competicion
            player.updatePlayedMatches(templateMatchEvent.optaSeasonId, templateMatchEvent.optaCompetitionId);

            team.soccerPlayers.add(player);
        }
        return team;
    }
}
