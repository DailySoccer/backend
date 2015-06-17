package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class TemplateContest implements JongoId {
    public static final String FILL_WITH_MOCK_USERS = "%MockUsers";

    @Id
    public ObjectId templateContestId;

    @JsonView(JsonViews.Extended.class)
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(JsonViews.NotForClient.class)
    public int minInstances;        // Minimum desired number of instances that we want running at any given moment

    public int maxEntries;

    public int salaryCap;
    public Money entryFee;
    public PrizeType prizeType;

    public Date startDate;

    public String optaCompetitionId;

    @JsonView(JsonViews.Extended.class)
    public List<ObjectId> templateMatchEventIds;

    @JsonView(JsonViews.Extended.class)
    public List<InstanceSoccerPlayer> instanceSoccerPlayers;

    @JsonView(JsonViews.NotForClient.class)
    public Date activationAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public boolean simulated = false;

    public TemplateContest() { }

    public TemplateContest(String name, int minInstances, int maxEntries, SalaryCap salaryCap,
                            Money entryFee, PrizeType prizeType, Date activationAt,
                            List<String> templateMatchEvents) {

        this.name = name;
        this.minInstances = minInstances;
        this.maxEntries = maxEntries;
        this.salaryCap = salaryCap.money;
        this.entryFee = entryFee;
        this.prizeType = prizeType;
        this.activationAt = activationAt;

        Date startDate = null;
        this.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId : templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOneFromOptaId(templateMatchEventId);
            this.optaCompetitionId = templateMatchEvent.optaCompetitionId;
            this.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        this.startDate = startDate;
    }

    public TemplateContest copy() {
        TemplateContest cloned = new TemplateContest();

        cloned.templateContestId = templateContestId;
        cloned.state = state;
        cloned.name = name;
        cloned.minInstances = minInstances;
        cloned.maxEntries = maxEntries;

        cloned.salaryCap = salaryCap;
        cloned.entryFee = entryFee;
        cloned.prizeType = prizeType;

        cloned.startDate = startDate;
        cloned.optaCompetitionId = optaCompetitionId;

        cloned.templateMatchEventIds = new ArrayList<>(templateMatchEventIds);

        if (instanceSoccerPlayers != null) {
            cloned.instanceSoccerPlayers = new ArrayList<>(instanceSoccerPlayers);
        }

        cloned.activationAt = activationAt;
        cloned.createdAt = createdAt;

        return cloned;
    }

    public ObjectId getId() {
        return templateContestId;
    }

    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
    }

    static public TemplateContest findOne(ObjectId templateContestId) {
        return Model.templateContests().findOne("{_id : #}", templateContestId).as(TemplateContest.class);
    }

    static public TemplateContest findOne(String templateContestId) {
        TemplateContest theTemplateContest = null;
        if (ObjectId.isValid(templateContestId)) {
            theTemplateContest = Model.templateContests().findOne("{_id : #}", new ObjectId(templateContestId)).as(TemplateContest.class);
        }
        return theTemplateContest;
    }

    static public List<TemplateContest> findAll() {
        return ListUtils.asList(Model.templateContests().find().as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllActive() {
        return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\"}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllFromContests(List<Contest> contests) {

        ArrayList<ObjectId> templateContestIds = new ArrayList<>(contests.size());

        for (Contest contest : contests) {
            templateContestIds.add(contest.templateContestId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.templateContests(), "_id", templateContestIds).as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllByActivationAt(Date activationAt) {
        return ListUtils.asList(Model.templateContests()
                                     .find("{state: \"OFF\", activationAt: {$lte: #}, startDate: {$gte: #}}", activationAt, GlobalDate.getCurrentDate())
                                     .as(TemplateContest.class));
    }

    public TemplateContest insert() {
        Model.templateContests().withWriteConcern(WriteConcern.SAFE).update("{_id: #}", templateContestId).upsert().with(this);
        return this;
    }

    /**
     *  Eliminar un template contest y sus dependencias
     */
    public static boolean remove(TemplateContest templateContest) {
        Logger.info("remove TemplateContest({}): {}", templateContest.templateContestId, templateContest.name);

        // Eliminar el template contest
        try {
            WriteResult result = Model.templateContests().remove("{_id: #, $or: [{state: \"DRAFT\"}, {state: \"OFF\"}]}", templateContest.templateContestId);
            if (result.getN() == 0) {
                throw new RuntimeException(String.format("Template Contest: Error removing %s", templateContest.templateContestId.toString()));
            }
        }
        catch(MongoException e) {
            Logger.error("WTF 6799", e);
        }

        return true;
    }

    public static void maintainingMinimumNumberOfInstances(ObjectId templateContestId) {
        TemplateContest templateContest = findOne(templateContestId);

        // Cuantas instancias tenemos creadas?
        long instances = Contest.countActiveNotFullFromTemplateContest(templateContestId);

        for (long i=instances; i < templateContest.minInstances; i++) {
            templateContest.instantiateContest();
        }
    }

    public Contest instantiateContest() {
        Contest contest = new Contest(this);
        contest.state = ContestState.ACTIVE;
        Model.contests().withWriteConcern(WriteConcern.SAFE).insert(contest);
        return contest;
    }

    public void instantiate() {

        Logger.info("TemplateContest.instantiate: {}: activationAt: {}", name, GlobalDate.formatDate(activationAt));

        registerSoccerPlayers();

        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContestId);

        boolean mockDataUsers = name.contains(FILL_WITH_MOCK_USERS);
        for (long i=instances; i < minInstances; i++) {
            Contest contest = instantiateContest();
            if (mockDataUsers) {
                MockData.addContestEntries(contest, contest.maxEntries - 1);
            }
        }

        // Cuando hemos acabado de instanciar nuestras dependencias, nos ponemos en activo
        Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContestId).with("{$set: {state: \"ACTIVE\", instanceSoccerPlayers:#}}", instanceSoccerPlayers);

        // Ya estamos activos!
        state = ContestState.ACTIVE;
    }

    private void registerSoccerPlayers() {
        instanceSoccerPlayers = new ArrayList<>();

        List<TemplateMatchEvent> templateMatchEvents = getTemplateMatchEvents();
        for (TemplateMatchEvent templateMatchEvent: templateMatchEvents) {
            List<TemplateSoccerPlayer> templateSoccerPlayers = templateMatchEvent.getTemplateSoccerPlayersActives();
            for (TemplateSoccerPlayer templateSoccerPlayer: templateSoccerPlayers) {
                instanceSoccerPlayers.add(new InstanceSoccerPlayer(templateSoccerPlayer));
            }
        }
    }

    static public TemplateContest createSimulation(ObjectId templateContestId) {
        TemplateContest simulation = TemplateContest.findOne(templateContestId).copy();
        simulation.templateContestId = new ObjectId();

        // Crear partidos "simulados"
        simulation.templateMatchEventIds = simulation.templateMatchEventIds.stream().map(matchEventId -> {
            TemplateMatchEvent simulateMatchEvent = TemplateMatchEvent.createSimulation(matchEventId);
            return simulateMatchEvent.templateMatchEventId;
        }).collect(Collectors.toList());

        /*
        if (!simulation.name.contains(FILL_WITH_MOCK_USERS)) {
            simulation.name = simulation.name.concat(" ").concat(FILL_WITH_MOCK_USERS);
        }
        */

        simulation.insert().setSimulation();

        simulation.instantiate();
        return simulation;
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        // El TemplateContest ha comenzado si cualquiera de sus partidos ha comenzado
        TemplateMatchEvent matchEventStarted = Model.templateMatchEvents()
                .findOne("{_id: {$in: #}, gameStartedDate: {$exists: 1}}", templateMatchEventIds)
                .projection("{_id: 1}")
                .as(TemplateMatchEvent.class);
        return (matchEventStarted != null);
    }

    public static boolean isStarted(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isStarted();
    }

    public boolean isFinished() {
        // El TemplateContest ha terminado si todos sus partidos han terminado
        long numMatchEventsFinished = Model.templateMatchEvents()
                .count("{_id: {$in: #}, gameFinishedDate: {$exists: 1}}", templateMatchEventIds);
        return (numMatchEventsFinished == templateMatchEventIds.size());
    }

    public static boolean isFinished(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isFinished();
    }

    public static void publish(ObjectId templateContestId) {
        Model.templateContests().update("{_id: #, state: \"DRAFT\"}", templateContestId).with("{$set: {state: \"OFF\"}}");
    }

    public void setSimulation() {
        simulated = true;
        Model.templateContests().update("{_id: #}", templateContestId).with("{$set: {simulated: #}}", simulated);
    }
}
