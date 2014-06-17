package controllers.admin;

import model.*;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import model.opta.OptaEvent;
import com.mongodb.*;
import org.bson.types.ObjectId;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static play.data.Form.form;


public class AdminController extends Controller {

    public static Result dashBoard() {
        return ok(views.html.dashboard.render(null));
    }

    public static Result resetDB() {
        Model.resetDB();
        return ok(views.html.dashboard.render(FlashMessage.success("Reset DB: OK")));
    }

    public static Result createMockDataDB() {
        Model.resetDB();
        MockData.ensureMockDataAll();
        return ok(views.html.dashboard.render(FlashMessage.success("Reset DB with Mock Data")));
    }

    public static Result resetContests() {
        Model.resetContests();
        return ok(views.html.dashboard.render(FlashMessage.success("Reset Contests: OK")));
    }

    public static Result createMockDataContests() {
        Model.resetContests();
        MockData.ensureMockDataContests();
        return ok(views.html.dashboard.render(FlashMessage.success("Reset Contests with Mock data: OK")));
    }

    public static Result importTeamsAndSoccers() {
        Model.resetContests();
        Model.importTeamsAndSoccersFromOptaDB();
        return ok(views.html.dashboard.render(FlashMessage.success("Import Teams & Soccers: OK")));
    }

    public static Result importMatchEvents() {
        Model.importMatchEventsFromOptaDB();
        return ok(views.html.dashboard.render(FlashMessage.success("Import Match Events: OK")));
    }

    public static Result lobby() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.listFromIterator(contestsResults.iterator());

        HashMap<ObjectId, TemplateContest> templateContestMap = getTemplateContestsFromList(contestList);

        return ok(views.html.lobby.render(contestList, templateContestMap));
    }

    public static Result users() {
        Iterable<User> userResults = Model.users().find().as(User.class);
        List<User> userList = ListUtils.listFromIterator(userResults.iterator());

        return ok(views.html.user_list.render(userList));
    }

    public static Result updateLive() {
        // Obtenemos la lista de TemplateMatchEvents
        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.templateMatchEvents().find().as(TemplateMatchEvent.class);

        // Existira un live Match Event por cada template Match Event
        for (TemplateMatchEvent templateMatchEvent : templateMatchEventsResults) {
            LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);
            Model.liveMatchEvents().update("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).upsert().with(liveMatchEvent);

            // Actualizar los fantasy points de cada live match event
            Model.updateLiveFantasyPoints(liveMatchEvent);
        }

        return redirect(routes.AdminController.liveMatchEvents());
    }

    public static Result liveMatchEvent(String liveMatchEventId) {
        LiveMatchEvent liveMatchEvent = Model.liveMatchEvents().findOne("{ _id : # }",
                new ObjectId(liveMatchEventId)).as(LiveMatchEvent.class);
        return ok(views.html.live_match_event.render(liveMatchEvent));
    }

    public static Result liveMatchEvents() {
        Iterable<LiveMatchEvent> liveMatchEventResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        return ok(views.html.live_match_event_list.render(liveMatchEventList));
    }

    public static Result createPointsTranslation(){
        MockData.createPointsTranslation();
        return redirect(routes.AdminController.pointsTranslations());
    }

    public static Result pointsTranslations() {
        Iterable<PointsTranslation> pointsTranslationsResults = Model.pointsTranslation().find().as(PointsTranslation.class);
        List<PointsTranslation> pointsTranslationsList = ListUtils.listFromIterator(pointsTranslationsResults.iterator());

        return ok(views.html.points_translation_list.render(pointsTranslationsList));
    }

    public static Result addContestEntry() {
        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class);
        return ok(views.html.contest_entry_add.render(contestEntryForm));
    }

    public static Result submitContestEntry() {
        Form<ContestEntryForm> contestEntryForm = form(ContestEntryForm.class).bindFromRequest();
        if (contestEntryForm.hasErrors()) {
            return badRequest(views.html.contest_entry_add.render(contestEntryForm));
        }

        ContestEntryForm params = contestEntryForm.get();
        Logger.info("UserId({}) Contest({})Goalkeeper({}) Defenses({}, {}, {}, {}) Middles({}, {}, {}, {}), Forwards({}, {})",
                params.userId, params.contestId,
                params.goalkeeper,
                params.defense1, params.defense2, params.defense3, params.defense4,
                params.middle1, params.middle2, params.middle3, params.middle4,
                params.forward1, params.forward2);

        /*
         *
         * TODO
         *
        boolean success = ContestController.createContestEntryFromOptaIds(params.userId, params.contestId, params.getTeam());
        if ( !success ) {
            return badRequest(views.html.contest_entry_add.render(contestEntryForm));
        }
        */

        return redirect(routes.AdminController.contestEntries());
    }

    public static Result contestEntries() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.listFromIterator(contestEntryResults.iterator());

        return ok(views.html.contest_entry_list.render(contestEntryList));
    }

    public static Result contests() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.listFromIterator(contestsResults.iterator());

        return ok(views.html.contest_list.render(contestList));
    }

    public static Result contest(String contestId) {
        Contest contest = Model.contests().findOne("{ _id : # }", new ObjectId(contestId)).as(Contest.class);
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0, "hola");
        map.put(1, "adios");
        return ok(views.html.contest.render(contest, map));
    }

    public static Result instantiateContests() {
        Iterable<TemplateContest> templateContests = Model.templateContests().find().as(TemplateContest.class);
        for(TemplateContest template : templateContests) {
            instantiateTemplateContest(template);
        }

        return redirect(routes.AdminController.contests());
    }

    public static void instantiateTemplateContest(TemplateContest templateContest) {
        for(int i=0; i<templateContest.minInstances; i++) {
            Contest contest = new Contest(templateContest);
            contest.maxUsers = 10;
            Model.contests().insert(contest);
        }
    }

    public static Result templateContests() {
        Iterable<TemplateContest> templateContestResults = Model.templateContests().find().as(TemplateContest.class);
        List<TemplateContest> templateContestList = ListUtils.listFromIterator(templateContestResults.iterator());

        return ok(views.html.template_contest_list.render(templateContestList));
    }

    public static Result templateContest(String templateContestId) {
        return TODO;
    }

    public static Result createAllTemplateContests() {
        Model.templateContests().remove();

        Iterable<TemplateMatchEvent> matchEventResults = Model.templateMatchEvents().find().sort("{startDate: 1}").as(TemplateMatchEvent.class);

        DateTime dateTime = null;
        List<TemplateMatchEvent> matchEvents = new ArrayList<>();   // Partidos que juntaremos en el mismo contests
        for (TemplateMatchEvent match: matchEventResults) {
            DateTime matchDateTime = new DateTime(match.startDate);
            if (dateTime == null) {
                dateTime = matchDateTime;
            }

            // El partido es de un dia distinto?
            if (dateTime.dayOfYear().get() != matchDateTime.dayOfYear().get()) {
                Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEvents.size() >= 2) {

                    // crear el contest
                    createTemplateContest(matchEvents);

                    // empezar a registrar los partidos del nuevo contest
                    matchEvents.clear();
                }
            }

            dateTime = matchDateTime;
            matchEvents.add(match);
        }

        // Tenemos partidos sin incluir en un contest?
        if (matchEvents.size() > 0) {
            createTemplateContest(matchEvents);
        }


        return redirect(routes.AdminController.templateContests());
    }

    public static void createTemplateContest(List<TemplateMatchEvent> templateMatchEvents) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("createTemplateContest: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateContest.name = String.format("Contest date %s", startDate);
        templateContest.postName = "Late evening";
        templateContest.minInstances = 3;
        templateContest.maxEntries = 10;
        templateContest.prizeType = PrizeType.STANDARD;
        templateContest.entryFee = 10000;
        templateContest.salaryCap = 100000;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, startDate);

        Model.templateContests().insert(templateContest);
    }


    public static Result templateMatchEvents() {
        Iterable<TemplateMatchEvent> soccerMatchEventResults = Model.templateMatchEvents().find().as(TemplateMatchEvent.class);
        List<TemplateMatchEvent> matchEventList = ListUtils.listFromIterator(soccerMatchEventResults.iterator());

        return ok(views.html.template_match_event_list.render(matchEventList));
    }

    public static Result templateMatchEvent(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = Model.templateMatchEvents().findOne("{ _id : # }",
                new ObjectId(templateMatchEventId)).as(TemplateMatchEvent.class);
        return ok(views.html.template_match_event.render(templateMatchEvent));
    }

    public static Result createTemplateMatchEvent() {
        //TODO: En que fecha tendriamos que generar el partido?
        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        MockData.createTemplateMatchEvent(currentCreationDay);

        return redirect(routes.AdminController.templateMatchEvents());
    }

    public static Result templateSoccerTeams() {
        Iterable<TemplateSoccerTeam> soccerTeamResults = Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class);
        List<TemplateSoccerTeam> soccerTeamList = ListUtils.listFromIterator(soccerTeamResults.iterator());

        return ok(views.html.template_soccer_team_list.render(soccerTeamList));
    }

    public static Result templateSoccerTeam(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = Model.templateSoccerTeams().findOne("{ _id : # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerTeam.class);
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find("{ templateTeamId: # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.listFromIterator(soccerPlayerResults.iterator());
        return ok(views.html.template_soccer_team.render(templateSoccerTeam, soccerPlayerList));
    }

    public static Result templateSoccerPlayers() {
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.listFromIterator(soccerPlayerResults.iterator());

        return ok(views.html.template_soccer_player_list.render(soccerPlayerList));
    }

    public static HashMap<ObjectId, TemplateContest> getTemplateContestsFromList(List<Contest> contestList) {
        // Obtener la lista de Ids de los TemplateContests
        ArrayList<ObjectId> idsList = new ArrayList<>();
        for (Contest contest : contestList) {
            idsList.add(contest.templateContestId);
        }

        // Obtenemos las lista de TemplateContest de todos los Ids
        Iterable<TemplateContest> templateContestResults = Model.findTemplateContestsFromIds("_id", idsList);

        // Convertirlo a map
        HashMap<ObjectId, TemplateContest> ret = new HashMap<>();

        for (TemplateContest template : templateContestResults) {
            ret.put(template.templateContestId, template);
        }

        return ret;
    }

    public static Result optaSoccerPlayers() {
        Iterable<OptaPlayer> optaPlayerResults = Model.optaPlayers().find().as(OptaPlayer.class);
        List<OptaPlayer> optaPlayerList = ListUtils.listFromIterator(optaPlayerResults.iterator());

        return ok(views.html.opta_soccer_player_list.render(optaPlayerList));
    }

    public static Result optaSoccerTeams() {
        Iterable<OptaTeam> optaTeamResults = Model.optaTeams().find().as(OptaTeam.class);
        List<OptaTeam> optaTeamList = ListUtils.listFromIterator(optaTeamResults.iterator());

        return ok(views.html.opta_soccer_team_list.render(optaTeamList));
    }

    public static Result optaMatchEvents() {
        Iterable<OptaMatchEvent> optaMatchEventResults = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
        List<OptaMatchEvent> optaMatchEventList = ListUtils.listFromIterator(optaMatchEventResults.iterator());

        return ok(views.html.opta_match_event_list.render(optaMatchEventList));
    }

    public static Result launchSimulator(){
        if (!OptaSimulator.existsInstance()){
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.launch(0L, System.currentTimeMillis(), null);
            //Launch as a Thread, runs and parses all documents as fast as possible
            mySimulator.start();
            return ok("Simulator started");
        }
        return ok("Simulator already started");
    }

    public static Result stopSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        mySimulator.stop();
        return ok("Simulator stopped");
    }

    //TODO: WIP
    public static Result startSimulator(){
        if (!OptaSimulator.existsInstance()){
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.launch(0L, System.currentTimeMillis(), null);
            return ok("Simulator started");
        }
        return ok("Simulator already started");

    }

    //TODO: WIP
    public static Result simulatorNext(){
        if (!OptaSimulator.existsInstance()) {
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.next();
            return ok("Simulator next step");
        } else {
            return ok("Simulator not running");
        }

    }

     public static Result optaEvents() {
         Iterable<OptaEvent> optaEventResults = Model.optaEvents().find().as(OptaEvent.class);
         List<OptaEvent> optaEventList = ListUtils.listFromIterator(optaEventResults.iterator());

         return ok(views.html.opta_event_list.render(optaEventList));
    }
}