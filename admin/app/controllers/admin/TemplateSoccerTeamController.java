package controllers.admin;

import model.TemplateSoccerTeam;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;

public class TemplateSoccerTeamController extends Controller {
    public static Result index() {
        return ok(views.html.template_soccer_team_list.render(TemplateSoccerTeam.findAll()));
    }

    public static Result show(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(new ObjectId(templateSoccerTeamId));
        return ok(views.html.template_soccer_team.render(
                templateSoccerTeam,
                templateSoccerTeam.getTemplateSoccerPlayers()));
    }
}
