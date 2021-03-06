package controllers.admin;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import actions.CheckTargetEnvironment;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.WriteConcern;
import jdk.nashorn.internal.objects.Global;
import model.*;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jongo.Find;
import org.jongo.MongoCollection;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.FileUtils;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.ReturnHelper;

import static play.data.Form.form;

public class TemplateContestController extends Controller {

    static final String SEPARATOR_CSV = ";";
    static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm");

    // Test purposes
    public static int templateCount = 0;
    public static final String[] contestNameSuffixes = {"All stars", "Special", "Regular", "Professional",
            "Just 4 Fun", "Weekly", "Experts Only", "Friends", "Pro Level", "Friday Only", "Amateur", "Weekend Only", "For Fame & Glory"};

    public static Result index() {
        return ok(views.html.template_contest_list.render(getCreatingTemplateContestsState(), "*", ContestState.ACTIVE.toString(), OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    public static Result showFilterByCompetition(String optaCompetitionId, String stateId) {
        return ok(views.html.template_contest_list.render(getCreatingTemplateContestsState(), optaCompetitionId, stateId, OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    public static Result indexAjax(String seasonCompetitionId, String stateId) {
        HashMap<String, OptaCompetition> optaCompetitions = OptaCompetition.asMap(OptaCompetition.findAllActive());

        String query = null;
        if (optaCompetitions.containsKey(seasonCompetitionId)) {
            OptaCompetition optaCompetition = optaCompetitions.get(seasonCompetitionId);
            query = String.format("{optaCompetitionId: '%s', state: '%s', startDate: {$gt: #}}", optaCompetition.competitionId, stateId);
        }
        else {
            query = String.format("{state: '%s', startDate: {$gt: #}}", stateId);
        }

        return PaginationData.withAjaxAndQuery(request().queryString(), Model.templateContests(), query, TemplateContest.class, new PaginationData() {

            public long count(MongoCollection collection, String query) {
                return query != null ? collection.count(query, OptaCompetition.SEASON_DATE_START) : collection.count();
            }
            public Find find(MongoCollection collection, String query) {
                return query != null ? collection.find(query, OptaCompetition.SEASON_DATE_START) : collection.find();
            }

            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "state",
                        "name",
                        "",              // Num. Matches
                        "optaCompetitionId",
                        "minInstances",
                        "maxEntries",
                        "salaryCap",
                        "",             // Filters: ManagerLevel && TrueSkill
                        "entryFee",
                        "prizeMultiplier",
                        "",             // Prize Pool
                        "prizeType",
                        "startDate",
                        "activationAt",
                        "",             // Edit
                        "",             // Delete
                        ""              // Simulation

                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0:
                        return templateContest.state.toString();
                    case 1:
                        return templateContest.name + ((templateContest.customizable) ? "**" : "");
                    case 2:
                        return String.valueOf(templateContest.templateMatchEventIds.size());
                    case 3:
                        return templateContest.optaCompetitionId;
                    case 4:
                        return String.valueOf(templateContest.minInstances);
                    case 5:
                        return String.valueOf(templateContest.maxEntries);
                    case 6:
                        return String.valueOf(templateContest.salaryCap);
                    case 7:
                        return String.format("[%s:%s] [%s:%s]",
                                templateContest.minManagerLevel != null ? templateContest.minManagerLevel : "0",
                                templateContest.maxManagerLevel != null ? templateContest.maxManagerLevel : User.MANAGER_POINTS.length - 1,
                                templateContest.minTrueSkill != null && templateContest.minTrueSkill != -1 ? templateContest.minTrueSkill : "-",
                                templateContest.maxTrueSkill != null && templateContest.maxTrueSkill != -1 ? templateContest.maxTrueSkill : "-" );
                    case 8:
                        return MoneyUtils.asString(templateContest.entryFee);
                    case 9:
                        return String.valueOf(templateContest.prizeMultiplier);
                    case 10:
                        return MoneyUtils.asString(templateContest.getPrizePool());
                    case 11:
                        return String.valueOf(templateContest.prizeType);
                    case 12:
                        return GlobalDate.formatDate(templateContest.startDate);
                    case 13:
                        return GlobalDate.formatDate(templateContest.activationAt);
                    case 14:
                        return "";
                    case 15:
                        return "";
                    case 17:
                        return templateContest.simulation ? "Simulation" : "Real";
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0:
                        String classStyle = "btn btn-warning";
                        if (templateContest.state.isDraft()) classStyle = "btn btn-default";
                        else if (templateContest.state.isHistory()) classStyle = "btn btn-danger";
                        else if (templateContest.state.isLive()) classStyle = "btn btn-success";
                        else if (templateContest.state.isActive()) classStyle = "btn btn-warning";
                        return String.format("<a href=\"%s\"><button class=\"%s\">%s</button></a>",
                                routes.TemplateContestController.createClone(templateContest.templateContestId.toString()), classStyle, templateContest.state);
                    case 1:
                        return String.format("<a href=\"%s\" style=\"white-space: nowrap\">%s</a>",
                                routes.TemplateContestController.show(templateContest.templateContestId.toString()),
                                fieldValue);
                    case 4:
                        return String.format("%d/%s", templateContest.minInstances, templateContest.maxInstances <= 0 ? "-" : String.valueOf(templateContest.maxInstances));
                    case 5:
                        return String.format("%d/%d", templateContest.minEntries, templateContest.maxEntries);
                    case 14:
                        return (templateContest.state.isDraft() || templateContest.state.isOff() || templateContest.state.isActive())
                                ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">Edit</button></a>",
                                routes.TemplateContestController.edit(templateContest.templateContestId.toString()))
                                : "";
                    case 15:
                        return templateContest.state.isDraft()
                                ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">+</button></a> <a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                routes.TemplateContestController.publish(templateContest.templateContestId.toString()),
                                routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                                : templateContest.state.isOff() ?
                                String.format("<a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                        routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                                : "";
                    case 16:
                        return templateContest.simulation
                                ? String.format("<button class=\"btn btn-success\">Simulation</button>")
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

    public static Result newForm(int competitionId) {
        TemplateContestForm params = new TemplateContestForm();
        params.optaCompetitionId = String.valueOf(competitionId);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.optaCompetitionId, OptaCompetition.CURRENT_SEASON_ID), false));
    }

    public static Result edit(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        return edit(templateContest);
    }

    public static Result edit(TemplateContest templateContest) {
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        if (templateContest.state.isActive()) {
            List<TemplateMatchEvent> templateMatchEvents = templateContest.getTemplateMatchEvents();
            return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(templateMatchEvents), templateContest.state.isActive()));
        }
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(templateContest.optaCompetitionId, OptaCompetition.CURRENT_SEASON_ID), templateContest.state.isActive()));
    }

    public static Result createClone(String templateContestId) {
        TemplateContest clonedTemplateContest = TemplateContest.findOne(new ObjectId(templateContestId)).copy();
        clonedTemplateContest.templateContestId = null;
        clonedTemplateContest.state = ContestState.DRAFT;
        clonedTemplateContest.name += " (clone)";
        return edit(clonedTemplateContest);
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

    public static Result maintainNumInstances(String templateContestIdStr) {
        ObjectId templateContesId = new ObjectId(templateContestIdStr);
        TemplateContest.maintainingMinimumNumberOfInstances(templateContesId);

        FlashMessage.info("Instances Not Full: " + String.valueOf(TemplateContest.getNumInstancesNotFull(templateContesId)));

        return show(templateContestIdStr);
    }

    public static Result create() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            String createdAt = templateContestForm.field("createdAt").valueOr("0");
            String optaCompetitionId = templateContestForm.field("optaCompetitionId").valueOr("23");
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(optaCompetitionId, OptaCompetition.CURRENT_SEASON_ID), false));
        }

        TemplateContestForm params = templateContestForm.get();

        boolean isNew = params.id.isEmpty();

        TemplateContest templateContest = new TemplateContest();

        templateContest.templateContestId = !isNew ? new ObjectId(params.id) : null;
        templateContest.state = params.state;
        templateContest.customizable = params.typeCustomizable.equals(TemplateContestForm.SelectionYESNO.YES);
        templateContest.simulation = (params.typeContest == TypeContest.VIRTUAL);
        templateContest.name = params.name;
        templateContest.minInstances = params.minInstances;
        templateContest.maxInstances = params.maxInstances;
        templateContest.minEntries = params.minEntries;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap.money;

        templateContest.minManagerLevel = params.minManagerLevel;
        templateContest.maxManagerLevel = params.maxManagerLevel;

        templateContest.minTrueSkill = params.minTrueSkill != -1 ? params.minTrueSkill : null;
        templateContest.maxTrueSkill = params.maxTrueSkill != -1 ? params.maxTrueSkill : null;

        // En la simulación usaremos ENERGY, en los reales GOLD
        templateContest.entryFee = Money.zero(templateContest.simulation ? MoneyUtils.CURRENCY_ENERGY : MoneyUtils.CURRENCY_GOLD).plus(params.entryFee);
        templateContest.prizeType = params.prizeType;
        templateContest.prizeMultiplier = templateContest.simulation ? params.prizeMultiplier : Prizes.PRIZE_MULTIPLIER_FOR_REAL_CONTEST;
        templateContest.prizePool = Money.zero(templateContest.simulation ? MoneyUtils.CURRENCY_MANAGER : MoneyUtils.CURRENCY_GOLD).plus(params.prizePool);

        templateContest.specialImage = params.specialImage;

        templateContest.activationAt = new DateTime(params.activationAt).withZone(DateTimeZone.UTC).toDate();
        templateContest.createdAt = new Date(params.createdAt);

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId : params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
            templateContest.optaCompetitionId = templateMatchEvent.optaCompetitionId;
            templateContest.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }

        // Si es una "simulación" la fecha de comienzo la leeremos del formulario
        if (templateContest.simulation) {
            templateContest.startDate = new DateTime(params.startDate).withZone(DateTimeZone.UTC).toDate();
        }
        else {
            // Si no es simulación, la fecha de comienzo es la del primer partido
            templateContest.startDate = startDate;
        }

        if (isNew) {
            Model.templateContests().insert(templateContest);
            OpsLog.onNew(templateContest);
        }
        else {
            Model.templateContests().update("{_id: #}", templateContest.templateContestId).with(templateContest);
            updateActiveContestsFromTemplate(templateContest);
            OpsLog.onChange(templateContest);
        }

        // printAsTablePlayerManagerLevel(templateContest, params.filterByDFP, params.filterByPlayedMatches, params.filterByDays);

        return redirect(routes.TemplateContestController.showFilterByCompetition(templateContest.optaCompetitionId, templateContest.state.toString()));
    }

    public static Result showManagerLevels(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        printAsTablePlayerManagerLevel(templateContest, TemplateSoccerPlayer.FILTER_BY_DFP, TemplateSoccerPlayer.FILTER_BY_PLAYED_MATCHES, TemplateSoccerPlayer.FILTER_BY_DAYS);
        return ok(views.html.template_contest.render(
                templateContest,
                templateContest.getTemplateMatchEvents(),
                TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result showSoccerPlayersStats(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));

        List<TemplateMatchEvent> templateMatchEvents = templateContest.getTemplateMatchEvents();

        List<String> optaMatchEventIds = templateMatchEvents.stream().map( match -> match.optaMatchEventId ).collect(Collectors.toList());

        HashMap<ObjectId, TemplateSoccerTeam> teamMap = TemplateSoccerTeam.findAllAsMap();

        List<Contest> contests = ListUtils.asList(
                Model.contests()
                        .find("{ templateContestId: # }", templateContest.templateContestId )
                        .projection("{ _id: 1, contestEntries: 1 }")
                        .as(Contest.class)
        );

        List<TemplateSoccerPlayer> bestGoalkeepers = bestSoccerPlayers(optaMatchEventIds, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> bestDefenses = bestSoccerPlayers(optaMatchEventIds, FieldPos.DEFENSE);
        List<TemplateSoccerPlayer> bestMiddles = bestSoccerPlayers(optaMatchEventIds, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> bestForwards = bestSoccerPlayers(optaMatchEventIds, FieldPos.FORWARD);

        List<TemplateSoccerPlayer> efficientGoalkeepers = efficientSoccerPlayers(templateContest, optaMatchEventIds, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> efficientDefenses = efficientSoccerPlayers(templateContest, optaMatchEventIds, FieldPos.DEFENSE);
        List<TemplateSoccerPlayer> efficientMiddles = efficientSoccerPlayers(templateContest, optaMatchEventIds, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> efficientForwards = efficientSoccerPlayers(templateContest, optaMatchEventIds, FieldPos.FORWARD);

        /*
        printAsTableSoccerPlayerStats(FieldPos.GOALKEEPER, bestGoalkeepers, teamMap);
        printAsTableSoccerPlayerStats(FieldPos.DEFENSE, bestDefenses, teamMap);
        printAsTableSoccerPlayerStats(FieldPos.MIDDLE, bestMiddles, teamMap);
        printAsTableSoccerPlayerStats(FieldPos.FORWARD, bestForwards, teamMap);
        */

        return ok(views.html.template_contest_soccer_players_stats.render(
                templateContest,
                contests,
                bestGoalkeepers, bestDefenses, bestMiddles, bestForwards,
                efficientGoalkeepers, efficientDefenses, efficientMiddles, efficientForwards,
                teamMap));
    }

    public static List<TemplateSoccerPlayer> bestSoccerPlayers(List<String> optaMatchEventIds, FieldPos fieldPos) {
        List<TemplateSoccerPlayer> result = ListUtils.asList(Model.templateSoccerPlayers()
                .find("{ fieldPos: #, \"stats.optaMatchEventId\": {$in: #} }",
                               fieldPos.toString(), optaMatchEventIds)
                .projection("{ _id: 1, name: 1, templateTeamId: 1, optaPlayerId: 1, \"stats.$\": 1 }")
                .as(TemplateSoccerPlayer.class)
        );

        result.sort(new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                return o2.stats.get(0).fantasyPoints - o1.stats.get(0).fantasyPoints;
            }
        });

        return result.subList(0, 4);
    }

    public static List<TemplateSoccerPlayer> efficientSoccerPlayers(TemplateContest templateContest, List<String> optaMatchEventIds, FieldPos fieldPos) {
        List<TemplateSoccerPlayer> result = ListUtils.asList(Model.templateSoccerPlayers()
                .find("{ fieldPos: #, \"stats.optaMatchEventId\": {$in: #} }",
                        fieldPos.toString(), optaMatchEventIds)
                .projection("{ _id: 1, name: 1, templateTeamId: 1, optaPlayerId: 1, salary : 1, \"stats.$\": 1 }")
                .as(TemplateSoccerPlayer.class)
        );

        result.forEach( soccerPlayer -> {
            InstanceSoccerPlayer instanceSoccerPlayer = templateContest.getInstanceSoccerPlayer(soccerPlayer.templateSoccerPlayerId);
            if (instanceSoccerPlayer != null) {
                soccerPlayer.salary = instanceSoccerPlayer.salary;
            }
        });

        result.sort(new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                float factor1 = (o1.stats.get(0).fantasyPoints > 0) ? ((float) o1.salary / o1.stats.get(0).fantasyPoints ) : Float.MAX_VALUE;
                float factor2 = (o2.stats.get(0).fantasyPoints > 0) ? ((float) o2.salary / o2.stats.get(0).fantasyPoints ) : Float.MAX_VALUE;
                return Float.compare(factor1, factor2);
            }
        });

        return result.subList(0, 4);
    }

    public enum FieldCSV {
        ID,
        NAME,
        STATE,
        SIMULATION,
        CUSTOMIZABLE,
        MIN_INSTANCES,
        MAX_INSTANCES,
        MIN_ENTRIES,
        MAX_ENTRIES,
        SALARY_CAP,
        ENTRY_FEE,
        MIN_MANAGERLEVEL,
        MAX_MANAGERLEVEL,
        MIN_TRUESKILL,
        MAX_TRUESKILL,
        PRIZE_TYPE,
        PRIZE_MULTIPLIER,
        PRIZE_POOL,
        START_DATE,
        ACTIVATION_AT,
        SPECIAL_IMAGE
    }

    public static Result defaultCSV() {
        List<String> headers = new ArrayList<>();
        for (FieldCSV fieldCSV : FieldCSV.values()) {
            headers.add(fieldCSV.toString());
        }

        List<String> body = new ArrayList<>();

        List<TemplateContest> templateContest = TemplateContest.findAllDraft();

        templateContest.forEach( template -> {
            body.add(template.templateContestId.toString());
            body.add(template.name);
            body.add(template.state.toString());
            body.add(String.valueOf(template.simulation));
            body.add(String.valueOf(template.customizable));
            body.add(String.valueOf(template.minInstances));
            body.add(String.valueOf(template.maxInstances));
            body.add(String.valueOf(template.minEntries));
            body.add(String.valueOf(template.maxEntries));
            body.add(String.valueOf(template.salaryCap));
            body.add(template.entryFee.toString());
            body.add(String.valueOf(template.minManagerLevel != null ? template.minManagerLevel : 0));
            body.add(String.valueOf(template.maxManagerLevel != null ? template.maxManagerLevel : User.MAX_MANAGER_LEVEL));
            body.add(String.valueOf(template.minTrueSkill != null ? template.minTrueSkill : -1));
            body.add(String.valueOf(template.maxTrueSkill != null ? template.maxTrueSkill : -1));
            body.add(template.prizeType.toString());
            body.add(String.valueOf(template.prizeMultiplier));
            body.add(template.prizePool != null
                    ? template.prizePool.toString()
                    : (template.simulation ? MoneyUtils.zero(MoneyUtils.CURRENCY_MANAGER.getCurrencyCode()) : MoneyUtils.zero(MoneyUtils.CURRENCY_GOLD.getCurrencyCode())).toString() );
            body.add(new DateTime(template.startDate).toString(dateTimeFormatter.withZoneUTC()));
            body.add(new DateTime(template.activationAt).toString(dateTimeFormatter.withZoneUTC()));
            body.add(template.specialImage);
        });

        String fileName = String.format("template-contests.csv");
        FileUtils.generateCsv(fileName, headers, body, SEPARATOR_CSV);

        FlashMessage.info(fileName);

        return redirect(routes.TemplateContestController.index());
    }

    public static Result importFromCSV() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart httpFile = body.getFile("csv");
        if (httpFile != null) {
            if (importFromFileCSV(httpFile.getFile())) {
                FlashMessage.success("CSV read successfully");
            }
            else {
                FlashMessage.danger("CSV error");
            }
            return redirect(routes.TemplateContestController.index());
        } else {
            FlashMessage.danger("Missing file, select one through \"Choose file\"");
            return redirect(routes.TemplateContestController.index());
        }
    }

    public static boolean importFromFileCSV(File file) {
        boolean result = false;

        List<TemplateContest> templateContests = new ArrayList<>();

        try {
            String line = "";
            String[] headers = null;

            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {

                String[] params = line.split(SEPARATOR_CSV);

                List<String> data = Arrays.asList(params);
                System.out.println(String.format("[%s]", Joiner.on(SEPARATOR_CSV).join(data)));

                // Cabecera?
                if (params[0].equals(FieldCSV.ID.toString())) {
                    headers = params;
                    continue;
                }

                if (headers == null)
                    continue;

                ObjectId templateContestId = new ObjectId(params[0]);
                TemplateContest templateContestMaster = TemplateContest.findOne(templateContestId);
                if (templateContestMaster == null)
                    throw new IllegalArgumentException();

                TemplateContest templateContest = templateContestMaster.copy();

                templateContest.templateContestId = new ObjectId();

                for (int i=0; i<params.length; i++) {
                    switch(FieldCSV.valueOf(headers[i])) {
                        case NAME:              templateContest.name = params[i]; break;
                        case STATE:             templateContest.state = ContestState.valueOf(params[i]); break;
                        case SIMULATION:        templateContest.simulation = Boolean.valueOf(params[i]);
                        case CUSTOMIZABLE:      templateContest.customizable = Boolean.valueOf(params[i]); break;
                        case MIN_INSTANCES:     templateContest.minInstances = Integer.valueOf(params[i]); break;
                        case MAX_INSTANCES:     templateContest.maxInstances = Integer.valueOf(params[i]); break;
                        case MIN_ENTRIES:       templateContest.minEntries = Integer.valueOf(params[i]); break;
                        case MAX_ENTRIES:       templateContest.maxEntries = Integer.valueOf(params[i]);
                        case SALARY_CAP:        templateContest.salaryCap = Integer.valueOf(params[i]); break;
                        case MIN_MANAGERLEVEL:  templateContest.minManagerLevel = Integer.valueOf(params[i]); break;
                        case MAX_MANAGERLEVEL:  templateContest.maxManagerLevel = Integer.valueOf(params[i]); break;
                        case MIN_TRUESKILL:     templateContest.minTrueSkill = Integer.valueOf(params[i]) != -1 ? Integer.valueOf(params[i]) : null; break;
                        case MAX_TRUESKILL:     templateContest.maxTrueSkill = Integer.valueOf(params[i]) != -1 ? Integer.valueOf(params[i]) : null; break;
                        case ENTRY_FEE:         templateContest.entryFee = Money.parse(params[i]); break;
                        case PRIZE_TYPE:        templateContest.prizeType = PrizeType.valueOf(params[i]); break;
                        case PRIZE_MULTIPLIER:  templateContest.prizeMultiplier = Float.valueOf(params[i]); break;
                        case PRIZE_POOL:        templateContest.prizePool = Money.parse(params[i]); break;
                        case START_DATE:        templateContest.startDate = DateTime.parse(params[i], dateTimeFormatter.withZoneUTC()).toDate(); break;
                        case ACTIVATION_AT:     templateContest.activationAt = DateTime.parse(params[i], dateTimeFormatter.withZoneUTC()).toDate(); break;
                        case SPECIAL_IMAGE:     templateContest.specialImage = params[i]; break;
                    }
                }

                templateContests.add(templateContest);
            }

            result = true;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (result) {
            templateContests.forEach(templateContest -> {
                templateContest.insert();
                OpsLog.onNew(templateContest);
            });
        }

        return result;
    }

    private static void printAsTablePlayerManagerLevel(TemplateContest templateContest, int filterByDFP, int filterByPlayedMatches, int filterByDays) {
        StringBuffer buffer = new StringBuffer();

        buffer.append("<h3>" + templateContest.name + "</h3>");
        buffer.append("<h5>Conditions: DFP >= " + filterByDFP + " | PlayedMatches >= " + filterByPlayedMatches + " | Days <= " + ((filterByDays > 0) ? filterByDays : "~") + "</h5>");
        buffer.append("<table border=\"1\" style=\"width:40%; text-align: center; \">\n" +
                "        <tr>\n" +
                "        <td><strong>Manager Level<strong></td>\n" +
                "        <td><strong>GoalKeepers<strong></td>\n" +
                "        <td><strong>Defense<strong></td>\n" +
                "        <td><strong>Middle<strong></td>\n" +
                "        <td><strong>Forward<strong></td>\n" +
                "        </tr>");
        for (int i=0; i<User.MANAGER_POINTS.length; i++) {
            List<TemplateSoccerPlayer> availables = TemplateSoccerPlayer.soccerPlayersAvailables(templateContest.templateMatchEventIds, i, filterByDFP, filterByPlayedMatches, filterByDays);
            Map<String, Long> frequency = TemplateSoccerPlayer.frequencyFieldPos(availables);

            buffer.append("<tr>");
            buffer.append("<td>" + i + "</td>");
            buffer.append("<td>" + frequency.get(FieldPos.GOALKEEPER.name()) + "</td>");
            buffer.append("<td>" + frequency.get(FieldPos.DEFENSE.name()) +"</td>");
            buffer.append("<td>" + frequency.get(FieldPos.MIDDLE.name()) +"</td>");
            buffer.append("<td>" + frequency.get(FieldPos.FORWARD.name()) +"</td>");

            buffer.append("</tr>");
        }
        buffer.append("</table>");

        FlashMessage.info(buffer.toString());
    }

    private static void printAsTableSoccerPlayerStats(FieldPos fieldPos, List<TemplateSoccerPlayer> soccerPlayers, Map<ObjectId, TemplateSoccerTeam> teamMap) {
        StringBuffer buffer = new StringBuffer();

        buffer.append("<h3>" + fieldPos.toString() + "</h3>");
        buffer.append("<table border=\"1\" style=\"width:80%; text-align: center; \">\n" +
                "        <tr>\n" +
                "        <td><strong> Num <strong></td>\n" +
                "        <td><strong> OptaPlayerId <strong></td>\n" +
                "        <td><strong> Name <strong></td>\n" +
                "        <td><strong> FantasyPoints <strong></td>\n" +
                "        <td><strong> Team <strong></td>\n" +
                "        <td><strong> OpponentTeam <strong></td>\n" +
                "        <td><strong> Fecha <strong></td>\n" +
                "        </tr>");
        for (int i=0; i<soccerPlayers.size(); i++) {
            TemplateSoccerPlayer soccerPlayer = soccerPlayers.get(i);

            SoccerPlayerStats stats = soccerPlayer.stats.get(0);

            buffer.append("<tr>");
            buffer.append("<td>" + i + "</td>");
            buffer.append("<td>" + soccerPlayer.optaPlayerId + "</td>");
            buffer.append("<td>" + soccerPlayer.name +"</td>");
            buffer.append("<td>" + stats.fantasyPoints +"</td>");
            buffer.append("<td>" + teamMap.get(soccerPlayer.templateTeamId).name +"</td>");
            buffer.append("<td>" + teamMap.get(stats.opponentTeamId).name +"</td>");
            buffer.append("<td>" + GlobalDate.formatDate(stats.startDate) +"</td>");

            buffer.append("</tr>");
        }
        buffer.append("</table>");

        FlashMessage.info(buffer.toString());
    }

    private static void updateActiveContestsFromTemplate(TemplateContest templateContest) {
        // Only name can change in Active contests
        Model.contests().withWriteConcern(WriteConcern.SAFE)
                .update("{templateContestId: #}", templateContest.getId())
                .multi()
                .with("{$set: {name: #, specialImage: #, minManagerLevel: #, maxManagerLevel: #, minTrueSkill: #, maxTrueSkill: #}}",
                        templateContest.name, templateContest.specialImage,
                        templateContest.minManagerLevel, templateContest.maxManagerLevel,
                        templateContest.minTrueSkill, templateContest.maxTrueSkill);
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
            Money money = Money.of(MoneyUtils.CURRENCY_GOLD, i);

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
        templateContest.maxInstances = 0;
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

    public static Result getPrizes(String prizeType, Integer maxEntries, Integer prizePool) {
        Prizes prizes = Prizes.findOne(PrizeType.valueOf(prizeType), maxEntries, Money.of(MoneyUtils.CURRENCY_DEFAULT, prizePool));
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
