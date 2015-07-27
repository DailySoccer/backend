package controllers.admin;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import actions.CheckTargetEnvironment;
import com.google.common.collect.ImmutableList;
import com.mongodb.WriteConcern;
import model.*;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;
import utils.ReturnHelper;

import static play.data.Form.form;

public class TemplateContestController extends Controller {

    // Test purposes
    public static int templateCount = 0;
    public static final String[] contestNameSuffixes = {"All stars", "Special", "Regular", "Professional",
            "Just 4 Fun", "Weekly", "Experts Only", "Friends", "Pro Level", "Friday Only", "Amateur", "Weekend Only", "For Fame & Glory"};

    public static Result index() {
        return ok(views.html.template_contest_list.render(getCreatingTemplateContestsState()));
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.templateContests(), TemplateContest.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "state",
                        "name",
                        "",              // Num. Matches
                        "optaCompetitionId",
                        "minInstances",
                        "maxEntries",
                        "salaryCap",
                        "entryFee",
                        "prizeType",
                        "startDate",
                        "activationAt",
                        "",             // Edit
                        ""              // Delete

                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0: return templateContest.state.toString();
                    case 1: return templateContest.name;
                    case 2: return String.valueOf(templateContest.templateMatchEventIds.size());
                    case 3: return templateContest.optaCompetitionId;
                    case 4: return String.valueOf(templateContest.minInstances);
                    case 5: return String.valueOf(templateContest.maxEntries);
                    case 6: return String.valueOf(templateContest.salaryCap);
                    case 7: return String.valueOf(templateContest.entryFee);
                    case 8: return String.valueOf(templateContest.prizeType);
                    case 9: return GlobalDate.formatDate(templateContest.startDate);
                    case 10: return GlobalDate.formatDate(templateContest.activationAt);
                    case 11: return "";
                    case 12: return "";
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0:
                        if      (templateContest.state.isDraft())     return String.format("<button class=\"btn btn-default\">%s</button>", templateContest.state);
                        else if (templateContest.state.isHistory())   return String.format("<button class=\"btn btn-danger\">%s</button>", templateContest.state);
                        else if (templateContest.state.isLive())      return String.format("<button class=\"btn btn-success\">%s</button>", templateContest.state);
                        else if (templateContest.state.isActive())    return String.format("<button class=\"btn btn-warning\">%s</button>", templateContest.state);
                        return String.format("<button class=\"btn btn-warning disabled\">%s</button>", templateContest.state);
                    case 1: return String.format("<a href=\"%s\" style=\"white-space: nowrap\">%s</a>",
                                routes.TemplateContestController.show(templateContest.templateContestId.toString()),
                                fieldValue);
                    case 10: return (templateContest.state.isDraft() || templateContest.state.isOff() || templateContest.state.isActive())
                                ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">Edit</button></a>",
                                        routes.TemplateContestController.edit(templateContest.templateContestId.toString()))
                                : "";
                    case 11: return templateContest.state.isDraft()
                             ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">+</button></a> <a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                        routes.TemplateContestController.publish(templateContest.templateContestId.toString()),
                                        routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                             : templateContest.state.isOff() ?
                                String.format("<a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                        routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                             : "";
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        return ok(views.html.template_contest.render(
                templateContest,
                templateContest.getTemplateMatchEvents(),
                TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result newForm() {
        TemplateContestForm params = new TemplateContestForm();

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt), false));
    }

    public static Result edit(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt), templateContest.state.isActive()));
    }

    public static Result publish(String templateContestId) {
        TemplateContest.publish(new ObjectId(templateContestId));
        return redirect(routes.TemplateContestController.index());
    }

    public static Result destroy(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContest.remove(templateContest);
        return redirect(routes.TemplateContestController.index());
    }

    public static Result create() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            String createdAt = templateContestForm.field("createdAt").valueOr("0");
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(Long.parseLong(createdAt)), false));
        }

        TemplateContestForm params = templateContestForm.get();

        boolean isNew = params.id.isEmpty();

        TemplateContest templateContest = new TemplateContest();

        templateContest.templateContestId = !isNew ? new ObjectId(params.id) : null;
        templateContest.state = params.state;
        templateContest.name = params.name;
        templateContest.minInstances = params.minInstances;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap.money;
        templateContest.entryFee = MoneyUtils.of(params.entryFee);
        templateContest.prizeType = params.prizeType;

        templateContest.activationAt = new DateTime(params.activationAt).withZoneRetainFields(DateTimeZone.UTC).toDate();
        templateContest.createdAt = new Date(params.createdAt);

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId: params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
            templateContest.optaCompetitionId = templateMatchEvent.optaCompetitionId;
            templateContest.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        templateContest.startDate = startDate;

        if (isNew) {
            Model.templateContests().insert(templateContest);
            OpsLog.onNew(templateContest);
        }
        else {
            Model.templateContests().update("{_id: #}", templateContest.templateContestId).with(templateContest);
            updateActiveContestsFromTemplate(templateContest);
            OpsLog.onChange(templateContest);
        }

        return redirect(routes.TemplateContestController.index());
    }

    private static void updateActiveContestsFromTemplate(TemplateContest templateContest) {
        // Only name can change in Active contests
        Model.contests().withWriteConcern(WriteConcern.SAFE).update("{templateContestId: #}", templateContest.getId()).multi().with("{$set: {name: #}}", templateContest.name);
    }

    @CheckTargetEnvironment
    public static Result createAll() {
        templateCount = 0;
        Iterable<TemplateMatchEvent> matchEventResults = Model.templateMatchEvents().find().sort("{startDate: 1}").as(TemplateMatchEvent.class);

        DateTime dateTime = null;
        List<TemplateMatchEvent> matchEvents = new ArrayList<>();   // Partidos que juntaremos en el mismo contests
        for (TemplateMatchEvent match: matchEventResults) {
            DateTime matchDateTime = new DateTime(match.startDate, DateTimeZone.UTC);
            if (dateTime == null) {
                dateTime = matchDateTime;
            }

            // El partido es de un dia distinto?
            if (dateTime.dayOfYear().get() != matchDateTime.dayOfYear().get()) {
                // Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEvents.size() >= 2) {

                    // crear el contest
                    createMock(matchEvents);

                    // empezar a registrar los partidos del nuevo contest
                    matchEvents.clear();
                }
            }

            dateTime = matchDateTime;
            matchEvents.add(match);
        }

        // Tenemos partidos sin incluir en un contest?
        if (matchEvents.size() > 0) {
            createMock(matchEvents);
        }

        return redirect(routes.TemplateContestController.index());
    }

    public static Result setCreatingTemplateContestsState(boolean state) {
        Model.actors().tell("ContestsActor", state ? "StartCreatingTemplateContests" : "StopCreatingTemplateContests");
        return redirect(routes.TemplateContestController.index());
    }

    public static void createMock(List<TemplateMatchEvent> templateMatchEvents) {
        createMock(templateMatchEvents, MoneyUtils.zero, 3, PrizeType.FREE, SalaryCap.EASY);
        //createMock(templateMatchEvents, 0, 5, PrizeType.FREE);
        //createMock(templateMatchEvents, 0, 10, PrizeType.FREE);
        createMock(templateMatchEvents, MoneyUtils.zero, 25, PrizeType.FREE, SalaryCap.STANDARD);


        for (int i = 1; i<=6; i++) {
            Money money = MoneyUtils.of(i);

            switch (i) {
                case 1:
                    createMock(templateMatchEvents, money, 2, PrizeType.WINNER_TAKES_ALL, SalaryCap.DIFFICULT); //DIFICIL
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.STANDARD); //MEDIO
                    break;
                case 2:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.STANDARD); //MEDIO
                    break;
                case 3:
                    createMock(templateMatchEvents, money, 5, PrizeType.TOP_THIRD_GET_PRIZES, SalaryCap.EASY); //FACIL
                    createMock(templateMatchEvents, money, 3, PrizeType.FIFTY_FIFTY, SalaryCap.DIFFICULT); //DIFICIL
                    break;
                case 4:
                    createMock(templateMatchEvents, money, 3, PrizeType.FIFTY_FIFTY, SalaryCap.STANDARD); //MEDIO
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.EASY); //FACIL
                    break;
                case 5:
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.DIFFICULT); //DIFICIL
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.STANDARD); //MEDIO
                    break;
                case 6:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.EASY); //FACIL
                    break;
            }
            /*
            createMock(templateMatchEvents, i, 2, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 3, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 5, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 10, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 25, PrizeType.WINNER_TAKES_ALL);

            //createMock(templateMatchEvents, i, 3, PrizeType.TOP_3_GET_PRIZES);
            //createMock(templateMatchEvents, i, 5, PrizeType.TOP_3_GET_PRIZES);
            createMock(templateMatchEvents, i, 10, PrizeType.TOP_3_GET_PRIZES);
            //createMock(templateMatchEvents, i, 25, PrizeType.TOP_3_GET_PRIZES);

            //createMock(templateMatchEvents, i, 3, PrizeType.TOP_THIRD_GET_PRIZES);
            createMock(templateMatchEvents, i, 5, PrizeType.TOP_THIRD_GET_PRIZES);
            //createMock(templateMatchEvents, i, 10, PrizeType.TOP_THIRD_GET_PRIZES);
            //createMock(templateMatchEvents, i, 25, PrizeType.TOP_THIRD_GET_PRIZES);

            createMock(templateMatchEvents, i, 3, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 5, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 10, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 25, PrizeType.FIFTY_FIFTY);*/
        }
    }

    private static void createMock(List<TemplateMatchEvent> templateMatchEvents, Money entryFee, int maxEntries, PrizeType prizeType, SalaryCap salaryCap) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateCount = (templateCount + 1) % contestNameSuffixes.length;

        templateContest.name = "%StartDate - " + contestNameSuffixes[templateCount] + " " + TemplateContest.FILL_WITH_MOCK_USERS;
        templateContest.minInstances = 3;
        templateContest.maxEntries = maxEntries;
        templateContest.prizeType = prizeType;
        templateContest.entryFee = entryFee;
        templateContest.salaryCap = salaryCap.money;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        templateContest.activationAt = new DateTime(startDate).minusDays(2).toDate();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.optaCompetitionId = match.optaCompetitionId;
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        // Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, GlobalDate.formatDate(startDate));

        Model.templateContests().insert(templateContest);
    }

    public static Result getPrizes(String prizeType, Integer maxEntries, Integer entryFee) {
        Prizes prizes = Prizes.findOne(PrizeType.valueOf(prizeType), maxEntries, MoneyUtils.of(entryFee));
        return new ReturnHelper(prizes.getAllValues()).toResult();
    }

    static private Boolean getCreatingTemplateContestsState() {
        Boolean response = Model.actors().tellAndAwait("ContestsActor", "GetCreatingTemplateContestsState");
        if (response != null) {
            _creatingTemplateContestState.set(response);
        }
        return _creatingTemplateContestState.get();
    }

    static private AtomicBoolean _creatingTemplateContestState = new AtomicBoolean();
}
