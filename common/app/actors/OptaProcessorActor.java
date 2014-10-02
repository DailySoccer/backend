package actors;


import akka.actor.UntypedActor;
import jobs.OptaProcessorJob;
import model.GlobalDate;
import model.MatchEvent;
import model.Model;
import model.TemplateContest;
import model.opta.OptaEvent;
import model.opta.OptaProcessor;
import org.apache.commons.dbutils.DbUtils;
import play.Logger;
import play.db.DB;
import scala.concurrent.duration.Duration;

import java.sql.*;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class OptaProcessorActor extends UntypedActor {

    public OptaProcessorActor() {
        _optaProcessor = new OptaProcessor();
    }

    public void onReceive(Object msg) {

        switch ((String)msg) {
            case "Start":
            case "Tick":
                // Reciclamos memoria (podriamos probar a dejar el cache y reciclar cada cierto tiempo...)
                _optaProcessor = new OptaProcessor();

                ensureNextDocument();
                processNextDocument();

                // Reeschudeleamos una llamada a nosotros mismos para el siguiente Tick
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            case "SimulatorProcessNextDocument":
                processNextDocument();
                break;

            case "SimulatorLoadNextDocument":
                ensureNextDocument();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    public static Date getLastProcessedDate() {
        OptaProcessorState state = OptaProcessorState.findOne();

        if (state != null && state.lastProcessedDate != null) {
            return state.lastProcessedDate;
        }
        else {
            return new Date(0L);
        }
    }

    private void ensureNextDocument() {

        // Somos una ensure, si el siguiente documento ya esta cargado, simplemente retornamos. _nextDocInfo se pone
        // a null en processNextDocument, a la espera de que se ordene asegurar el siguiente
        if (_nextDocInfo != null)
            return;

        ensureConnection();

        try {
            queryNextResultSet();

            if (_optaResultSet.next()) {
                _nextDocInfo = new NextDocInfo(_optaResultSet.getTimestamp("created_at"),
                                               _optaResultSet.getInt(1));
            }
            else {

                // TODO: _DocumentsPerQuery == 1

                // Volvemos a intentar leer. Si no hay mas resultados, ahora si, hemos llegado al final.
                queryNextResultSet();

                if (_optaResultSet.next()) {
                    _nextDocInfo = new NextDocInfo(_optaResultSet.getTimestamp("created_at"),
                                                   _optaResultSet.getInt(1));
                }
                else {
                    closeConnection();
                    Logger.info("Hemos llegado al ultimo documento XML");

                    _nextDocInfo = new NextDocInfo(null, -1);
                }
            }
        }
        catch (Exception e) {
            // Punto de recuperacion 1. Al saltar una excepcion no habremos cambiado _nextDocInfo y por lo tanto reintentaremos
            // TODO: Let it die?
            Logger.error("WTF 1533", e);
        }
    }

    private void processNextDocument() {
        try {
            processCurrentDocumentInResultSet(_optaResultSet, _optaProcessor);
            _nextDocInfo = null;
        }
        catch (Exception e) {
            // Punto de recuperacion 2. Al saltar una excepcion, no ponemos _nextDocInfo a null y por lo tanto reintentaremos
            Logger.error("WTF 7817", e);
        }
    }

    static private void processCurrentDocumentInResultSet(ResultSet resultSet, OptaProcessor processor) throws SQLException {

        // Evitamos que se produzcan actualizaciones simultaneas
        OptaProcessorState state = Model.optaProcessor().findAndModify("{stateId: #}", OptaProcessorState.UNIQUE_ID)
                                                        .upsert()
                                                        .with("{$set: {isProcessing: true}}")
                                                        .as(OptaProcessorState.class);

        if (state != null && state.isProcessing) {
            throw new RuntimeException("WTF 3885: Colision entre dos actores");
        }

        if (state == null) {
            state = new OptaProcessorState();
            state.lastProcessedDate = new Date(0L);
        }

        Date created_at = new Date(resultSet.getTimestamp("created_at").getTime());

        if (created_at.before(state.lastProcessedDate))
            throw new RuntimeException("WTF 9190");

        String sqlxml = resultSet.getString("xml");
        String name = resultSet.getString("name");
        String feedType = resultSet.getString("feed_type");
        String seasonId = resultSet.getString("season_id");
        String competitionId = resultSet.getString("competition_id");
        String gameId = resultSet.getString("game_id");

        Logger.info("OptaProcessorActor: {}, {}, {}, {}/{}", feedType, name, GlobalDate.formatDate(created_at), seasonId, competitionId);

        HashSet<String> changedOptaMatchEventIds = processor.processOptaDBInput(feedType, name, competitionId, seasonId, gameId, sqlxml);
        onOptaMatchEventIdsChanged(changedOptaMatchEventIds);

        state.lastProcessedDate = created_at;
        Model.optaProcessor().update("{stateId: #}", OptaProcessorState.UNIQUE_ID).with(state);
    }

    static private void onOptaMatchEventIdsChanged(HashSet<String> changedOptaMatchEventIds) {

        for (String optaGameId : changedOptaMatchEventIds) {

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (MatchEvent matchEvent : Model.matchEvents().find("{optaMatchEventId: #}", optaGameId).as(MatchEvent.class)) {

                // Los partidos que han terminado no los actualizamos
                if (matchEvent.isGameFinished())
                    continue;

                // Ya está marcado como Comenzado?
                boolean matchEventStarted = matchEvent.isGameStarted();

                // Si NO estaba Comenzado y AHORA SÍ ha comenzado, lo marcamos y lanzamos las acciones de matchEventIsStarted
                if (!matchEventStarted && OptaEvent.isGameStarted(matchEvent.optaMatchEventId)) {
                    matchEvent.setGameStarted();
                    actionWhenMatchEventIsStarted(matchEvent);
                    matchEventStarted = true;
                }

                // Si ha comenzado, actualizamos la información del "Live"
                if (matchEventStarted) {
                    matchEvent.updateState();

                    // Si HA TERMINADO, lo marcamos y lanzamos las acciones de matchEventIsFinished
                    if (!matchEvent.isGameFinished() && OptaEvent.isGameFinished(matchEvent.optaMatchEventId)) {
                        matchEvent.setGameFinished();
                        actionWhenMatchEventIsFinished(matchEvent);
                    }
                }
            }
        }
    }

    private static void actionWhenMatchEventIsStarted(MatchEvent matchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tienen que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");

        Model.contests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");
    }

    private static void actionWhenMatchEventIsFinished(MatchEvent matchEvent) {
        // Buscamos los template contests que incluyan ese partido y que esten en "LIVE"
        Iterable<TemplateContest> templateContests = Model.templateContests().find("{templateMatchEventIds: {$in:[#]}, state: \"LIVE\"}",
                matchEvent.templateMatchEventId).as(TemplateContest.class);

        for (TemplateContest templateContest : templateContests) {
            // Si el contest ha terminado (true si todos sus partidos han terminado)
            if (templateContest.isFinished()) {
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");
                Model.contests().update("{templateContestId: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                // Aqui es el único sitio donde se darán los premios
                templateContest.givePrizes();
            }
        }

        matchEvent.saveStats();
    }

    private void queryNextResultSet() throws SQLException {

        if (_optaResultSet == null || _optaResultSet.isAfterLast()) {

            DbUtils.closeQuietly(null, _stmt, _optaResultSet);
            _stmt = null;
            _optaResultSet = null;

            Date lastProcessedDate = OptaProcessorJob.getLastProcessedDate();

            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml WHERE created_at > '"
                                                + new Timestamp(lastProcessedDate.getTime()) +
                                                "' ORDER BY created_at LIMIT " + "1" + ";");
        }
    }

    private void ensureConnection() {
        if (_connection != null) {
            return;
        }
        _connection = DB.getConnection();
    }

    private void closeConnection() {
        DbUtils.closeQuietly(_connection, _stmt, _optaResultSet);

        _connection = null;
        _stmt = null;
        _optaResultSet = null;
    }


    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;
    OptaProcessor _optaProcessor;

    NextDocInfo _nextDocInfo;


    static private class OptaProcessorState {
        static final String UNIQUE_ID = "--OptaProcessorState--";

        public String stateId = UNIQUE_ID;
        public Date lastProcessedDate;
        public boolean isProcessing;

        static public OptaProcessorState findOne() {
            return Model.optaProcessor().findOne("{stateId: #}", OptaProcessorState.UNIQUE_ID).as(OptaProcessorState.class);
        }
    }


    public class NextDocInfo {
        final Date date;
        final int id;

        public NextDocInfo(Date d, int i) { date = d; id = i; }
    }
}
