package model.opta;

import model.*;
import play.Play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class OptaImporter {
    public static String OPS_LOG_IMPORT = "IMPORT";

    public OptaImporter(OptaProcessor processor) {
        _optaTeamIds = processor.getDirtyTeamIds();
        _optaPlayerIds = processor.getDirtyPlayerIds();
        _optaMatchEventIds = processor.getDirtyMatchEventIds();
    }

    public OptaImporter(HashSet<String> optaTeamIds, HashSet<String> optaPlayerIds, HashSet<String> optaMatchEventIds) {
        _optaTeamIds = optaTeamIds;
        _optaPlayerIds = optaPlayerIds;
        _optaMatchEventIds = optaMatchEventIds;
    }

    public void process() {
        importTeams();
        importPlayers();
        importMatchEvents();
    }

    private void importTeams() {
        if (_optaTeamIds == null || _optaTeamIds.isEmpty()) {
            return;
        }

        List<String> invalids = new ArrayList<>();
        List<String> competitionsActivated = OptaCompetition.asIds(OptaCompetition.findAllActive());
        Iterable<OptaTeam> teamsDirty = Model.optaTeams().find("{optaTeamId: {$in: #}, seasonCompetitionIds: {$in: #}}", _optaTeamIds, competitionsActivated).as(OptaTeam.class);
        for(OptaTeam optaTeam : teamsDirty) {
            TemplateSoccerTeam template = TemplateSoccerTeam.findOneFromOptaId(optaTeam.optaTeamId);
            if (template == null) {
                if (TemplateSoccerTeam.isInvalidFromImport(optaTeam)) {
                    invalids.add(optaTeam.optaTeamId);
                    OpsLog.onInvalidate(OPS_LOG_IMPORT, optaTeam);
                }
                else {
                    template = new TemplateSoccerTeam(optaTeam);
                    template.updateDocument();
                    OpsLog.onNew(OPS_LOG_IMPORT, optaTeam);
                }
            }
            else if (template.hasChanged(optaTeam)) {
                template.changeDocument(optaTeam);
                OpsLog.onChange(OPS_LOG_IMPORT, optaTeam);
            }
        }
        Model.optaTeams().update("{optaTeamId: {$not: {$in: #}}}", invalids).multi().with("{$set: {dirty: false}}");
    }

    private void importPlayers() {
        if (_optaPlayerIds == null || _optaPlayerIds.isEmpty()) {
            return;
        }

        List<String> invalids = new ArrayList<>();
        HashMap<String, TemplateSoccerTeam> teamCache = new HashMap<>();
        Iterable<OptaPlayer> playersDirty = Model.optaPlayers().find("{optaPlayerId: {$in: #}}", _optaPlayerIds).as(OptaPlayer.class);
        for(OptaPlayer optaPlayer : playersDirty) {
            TemplateSoccerTeam templateTeam;
            if (teamCache.containsKey(optaPlayer.teamId)) {
                templateTeam = teamCache.get(optaPlayer.teamId);
            }
            else {
                templateTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);
                teamCache.put(optaPlayer.teamId, templateTeam);
            }

            if (templateTeam != null) {
                TemplateSoccerPlayer templatePlayer = TemplateSoccerPlayer.findOneFromOptaId(optaPlayer.optaPlayerId);
                if (templatePlayer == null) {
                    if (TemplateSoccerPlayer.isInvalidFromImport(optaPlayer)) {
                        invalids.add(optaPlayer.optaPlayerId);
                        OpsLog.onInvalidate(OPS_LOG_IMPORT, optaPlayer);
                    }
                    else {
                        templatePlayer = new TemplateSoccerPlayer(optaPlayer, templateTeam.templateSoccerTeamId);

                        templatePlayer.salary = Play.application().configuration().getBoolean("debug_salary")?
                                                        templatePlayer.name.length()*500:
                                                        -1;

                        templatePlayer.updateDocument();
                        OpsLog.onNew(OPS_LOG_IMPORT, optaPlayer);
                    }
                } else if (templatePlayer.hasChanged(optaPlayer, templateTeam.templateSoccerTeamId)) {
                    templatePlayer.changeDocument(optaPlayer);
                    OpsLog.onChange(OPS_LOG_IMPORT, optaPlayer);
                }
            }
        }
        Model.optaPlayers().update("{optaPlayerId: {$not: {$in: #}}}", invalids).multi().with("{$set: {dirty: false}}");
    }

    private void importMatchEvents() {
        if (_optaMatchEventIds == null || _optaMatchEventIds.isEmpty()) {
            return;
        }

        List<String> invalids = new ArrayList<>();
        Iterable<OptaMatchEvent> matchesDirty = Model.optaMatchEvents().find("{optaMatchEventId: {$in: #}}", _optaMatchEventIds).as(OptaMatchEvent.class);
        for(OptaMatchEvent optaMatch : matchesDirty) {
            TemplateMatchEvent template = TemplateMatchEvent.findOneFromOptaId(optaMatch.optaMatchEventId);
            if (template == null) {
                if (TemplateMatchEvent.isInvalidFromImport(optaMatch)) {
                    invalids.add(optaMatch.optaMatchEventId);
                    OpsLog.onInvalidate(OPS_LOG_IMPORT, optaMatch);
                }
                else {
                    template = TemplateMatchEvent.createFromOpta(optaMatch);
                    template.updateDocument();
                    OpsLog.onNew(OPS_LOG_IMPORT, optaMatch);
                }
            }
            else if (template.hasChanged(optaMatch)) {
                template.changeDocument(optaMatch);
                OpsLog.onChange(OPS_LOG_IMPORT, optaMatch);
            }
        }
        Model.optaMatchEvents().update("{optaMatchEventId: {$not: {$in: #}}}", invalids).multi().with("{$set: {dirty: false}}");
    }

    HashSet<String> _optaTeamIds;
    HashSet<String> _optaPlayerIds;
    HashSet<String> _optaMatchEventIds;
}
