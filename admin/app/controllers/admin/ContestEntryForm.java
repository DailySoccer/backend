package controllers.admin;

import model.*;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class ContestEntryForm {
    @Constraints.Required
    public String userId;

    @Constraints.Required
    public String contestId;

    @Constraints.Required
    public String goalkeeper;

    @Constraints.Required
    public String defense1, defense2, defense3, defense4;

    @Constraints.Required
    public String middle1, middle2, middle3, middle4;

    @Constraints.Required
    public String forward1, forward2;

    public List<String> getTeam() {
        List<String> list = new ArrayList<>();
        list.add(goalkeeper);
        list.add(defense1);
        list.add(defense2);
        list.add(defense3);
        list.add(defense4);
        list.add(middle1);
        list.add(middle2);
        list.add(middle3);
        list.add(middle4);
        list.add(forward1);
        list.add(forward2);
        return list;
    }

    public void addSoccer(String optaPlayerId) {
        if (goalkeeper.isEmpty())       goalkeeper = optaPlayerId;
        else if (defense1.isEmpty())    defense1 = optaPlayerId;
        else if (defense2.isEmpty())    defense2 = optaPlayerId;
        else if (defense3.isEmpty())    defense3 = optaPlayerId;
        else if (defense4.isEmpty())    defense4 = optaPlayerId;
        else if (middle1.isEmpty())     middle1 = optaPlayerId;
        else if (middle2.isEmpty())     middle2 = optaPlayerId;
        else if (middle3.isEmpty())     middle3 = optaPlayerId;
        else if (middle4.isEmpty())     middle4 = optaPlayerId;
        else if (forward1.isEmpty())    forward1 = optaPlayerId;
        else if (forward2.isEmpty())    forward2 = optaPlayerId;
    }

    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        // Validar user
        User aUser = Model.findUserId(userId);
        if (aUser == null) {
            errors.add(new ValidationError("userId", "User invalid"));
        }

        // Validar contest
        Contest aContest = Model.findContestId(contestId);
        if (aContest == null) {
            errors.add(new ValidationError("contestId", "Contest invalid"));
        }

        // Validar cada uno de los futbolistas
        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", goalkeeper).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("goalkeeper", "Goalkeeper invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", defense1).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("defense1", "Defense invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", defense2).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("defense2", "Defense invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", defense3).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("defense3", "Defense invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", defense4).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("defense4", "Defense invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", middle1).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("middle1", "Middle invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", middle2).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("middle2", "Middle invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", middle3).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("middle3", "Middle invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", middle4).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("middle4", "Middle invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", forward1).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("forward1", "Forward invalid"));
        }

        if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", forward2).as(TemplateSoccerPlayer.class) == null) {
            errors.add(new ValidationError("forward2", "Forward invalid"));
        }

        if(errors.size() > 0)
            return errors;

        return null;
    }
}
