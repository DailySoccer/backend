package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import model.*;
import model.jobs.CancelContestEntryJob;
import model.jobs.EnterContestJob;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.data.Form.form;

@AllowCors.Origin
public class ContestEntryController extends Controller {

    private static final String CONTEST_ENTRY_KEY = "error";
    private static final String ERROR_CONTEST_INVALID = "ERROR_CONTEST_INVALID";
    private static final String ERROR_CONTEST_NOT_ACTIVE = "ERROR_CONTEST_NOT_ACTIVE";
    private static final String ERROR_SOURCE_SOCCERPLAYER_INVALID = "ERROR_SOURCE_SOCCERPLAYER_INVALID";
    private static final String ERROR_TARGET_SOCCERPLAYER_INVALID = "ERROR_TARGET_SOCCERPLAYER_INVALID";
    private static final String ERROR_CONTEST_FULL = "ERROR_CONTEST_FULL";
    private static final String ERROR_FANTASY_TEAM_INCOMPLETE = "ERROR_FANTASY_TEAM_INCOMPLETE";
    private static final String ERROR_SALARYCAP_INVALID = "ERROR_SALARYCAP_INVALID";
    private static final String ERROR_FORMATION_INVALID = "ERROR_FORMATION_INVALID";
    private static final String ERROR_CONTEST_ENTRY_INVALID = "ERROR_CONTEST_ENTRY_INVALID";
    private static final String ERROR_MANAGER_LEVEL_INVALID = "ERROR_MANAGER_LEVEL_INVALID";
    private static final String ERROR_TRUESKILL_INVALID = "ERROR_TRUESKILL_INVALID";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";
    private static final String ERROR_USER_ALREADY_INCLUDED = "ERROR_USER_ALREADY_INCLUDED";
    private static final String ERROR_USER_BALANCE_NEGATIVE = "ERROR_USER_BALANCE_NEGATIVE";
    private static final String ERROR_MAX_PLAYERS_SAME_TEAM = "ERROR_MAX_PLAYERS_SAME_TEAM";
    private static final String ERROR_RETRY_OP = "ERROR_RETRY_OP";

    public static class AddContestEntryParams {
        @Constraints.Required
        public String formation;

        @Constraints.Required
        public String contestId;

        @Constraints.Required
        public String soccerTeam;   // JSON con la lista de futbolistas seleccionados
    }

    /**
     * Añadir un contest entry
     *      (participacion de un usuario en un contest, por medio de la seleccion de un equipo de futbolistas)
     */
    @UserAuthenticated
    public static Result addContestEntry() {
        Form<AddContestEntryParams> contestEntryForm = form(AddContestEntryParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        // Donde nos solicitan que quieren insertarlo
        String contestIdRequested = "";

        // Id del contest que hemos encontrado
        ObjectId contestIdValid = null;
        String formation;

        if (!contestEntryForm.hasErrors()) {
            AddContestEntryParams params = contestEntryForm.get();

            contestIdRequested = params.contestId;
            formation = params.formation;

            // Buscar el contest : ObjectId
            Contest aContest = Contest.findOne(contestIdRequested);

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

            List<String> errores = validateContestEntry(aContest, formation, idsList, /*changeInLiveAllowed*/ false);

            if (errores.isEmpty()) {
                if (aContest.containsContestEntryWithUser(theUser.userId)) {
                    errores.add(ERROR_USER_ALREADY_INCLUDED);
                }
                // Verificar que el contest no esté lleno (<= 0 : Ilimitado número de participantes)
                else if (aContest.maxEntries >= 0 && aContest.contestEntries.size() >= aContest.maxEntries) {
                    // Dado que no creamos varias instancias de los torneos llenos, creados por los usuarios,
                    //   habrá que informar de que el torneo ya está lleno.
                    if (aContest.isCreatedByUser()) {
                        errores.add(ERROR_CONTEST_FULL);
                    }
                    else {
                        // Registramos el templateContestId antes de modificar el Contest (por si lo necesitamos más adelante)
                        ObjectId templateContestId = aContest.templateContestId;

                        // Buscar otro contest de características similares (el sistema tendría que crear una nueva instancia de características similares en algún momento...)
                        aContest = aContest.getSameContestWithFreeSlot(theUser.userId);
                        if (aContest == null) {
                            // Si no encontramos ningún Contest semejante, pedimos al webClient que lo intente otra vez
                            //  dado que asumimos que simplemente es un problema "temporal"
                            // Salvo que el templateContest tenga una limitación de instancias posibles
                            boolean limitInstances = TemplateContest.hasMaxInstances(templateContestId);
                            errores.add(limitInstances ? ERROR_CONTEST_FULL : ERROR_RETRY_OP);

                            if (limitInstances) {
                                Logger.info("LIMIT TemplateContest MaxInstances: {}", templateContestId.toString());
                            }
                        }
                    }
                }

                // Si tenemos un contest valido, registramos su ID
                if (aContest != null) {
                    contestIdValid = aContest.contestId;
                }
            }

            if (errores.isEmpty()) {
                if (MoneyUtils.isGreaterThan(aContest.entryFee, MoneyUtils.zero)) {
                    Money moneyNeeded = aContest.entryFee;
                    // En los torneos Oficiales, el usuario también tiene que pagar a los futbolistas
                    if (aContest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                        Money managerBalance = User.calculateManagerBalance(theUser.userId);

                        List<InstanceSoccerPlayer> soccerPlayers = aContest.getInstanceSoccerPlayers(idsList);
                        moneyNeeded = moneyNeeded.plus(User.moneyToBuy(aContest, managerBalance, soccerPlayers));
                        Logger.debug("addContestEntry: moneyNeeded: {}", moneyNeeded.toString());
                    }
                    // Verificar que el usuario tiene dinero suficiente...
                    if (!User.hasMoney(theUser.userId, moneyNeeded)) {
                        errores.add(ERROR_USER_BALANCE_NEGATIVE);
                    }
                }
            }

            if (errores.isEmpty() && aContest.hasManagerLevelConditions()) {
                Money managerBalance = User.calculateManagerBalance(theUser.userId);
                int managerLevel = (int) User.managerLevelFromPoints(managerBalance);
                if (!aContest.managerLevelValid(managerLevel)) {
                    errores.add(ERROR_MANAGER_LEVEL_INVALID);
                }
            }

            if (errores.isEmpty() && aContest.hasTrueSkillConditions()) {
                if (!aContest.trueSkillValid(theUser.trueSkill)) {
                    errores.add(ERROR_TRUESKILL_INVALID);
                }
            }

            if (errores.isEmpty()) {
                if (aContest == null) {
                    throw new RuntimeException("WTF 8639: aContest != null");
                }

                // Los contests creados por los usuarios se activan cuando el author entra un contestEntry
                if (aContest.state.isWaitingAuthor()) {
                    Model.contests().update("{_id: #, state: \"WAITING_AUTHOR\"}", aContest.contestId).with("{$set: {state: \"ACTIVE\"}}");

                    if (aContest.simulation) {
                        aContest.setupSimulation();
                    }

                    // Durante el desarrollo permitimos que los mockUsers puedan entrar en un contest
                    if (Play.isDev()) {
                        boolean mockDataUsers = aContest.name.contains(TemplateContest.FILL_WITH_MOCK_USERS);
                        if (mockDataUsers) {
                            MockData.addContestEntries(aContest, (aContest.maxEntries > 0) ? aContest.maxEntries - 1 : 50);
                        }
                    }
                }

                EnterContestJob enterContestJob = EnterContestJob.create(theUser.userId, contestIdValid, formation, idsList);

                // Al intentar aplicar el job puede que nos encontremos con algún conflicto (de última hora),
                //  lo volvemos a intentar para poder informar del error (con los tests anteriores)
                if (!enterContestJob.isDone()) {
                    errores.add(ERROR_RETRY_OP);
                }
            }

            for (String error : errores) {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
            }
        }

        Object result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            // El usuario ha sido añadido en el contest que solicitó
            //   o en otro de características semejantes (al estar lleno el anterior)
            if (contestIdValid.equals(contestIdRequested)) {
                Logger.info("addContestEntry: userId: {}: contestId: {}", theUser.userId.toString(), contestIdRequested);
            }
            else {
                Logger.info("addContestEntry: userId: {}: contestId: {} => {}", theUser.userId.toString(), contestIdRequested, contestIdValid.toString());
            }

            // Enviamos un perfil de usuario actualizado, dado que habrá gastado energía o gold al entrar en el constest
            result = ImmutableMap.of(
                    "result", "ok",
                    "contestId", contestIdValid.toString(),
                    "profile", User.findOne(theUser.userId).getProfile());
        }
        else {
            Logger.warn("addContestEntry failed: userId: {}: contestId: {}: error: {}", theUser.userId.toString(), contestIdRequested, contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    public static class EditContestEntryParams {
        @Constraints.Required
        public String formation;

        @Constraints.Required
        public String contestEntryId;

        @Constraints.Required
        public String soccerTeam;   // JSON con la lista de futbolistas seleccionados
    }

    @UserAuthenticated
    public static Result editContestEntry() {
        Form<EditContestEntryParams> contestEntryForm = form(EditContestEntryParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!contestEntryForm.hasErrors()) {
            EditContestEntryParams params = contestEntryForm.get();

            String formation = params.formation;

            Logger.info("editContestEntry: contestEntryId({}) formation({}) soccerTeam({})", params.contestEntryId, params.formation, params.soccerTeam);

            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                // Obtener el contestId : ObjectId
                Contest aContest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);

                // Obtener los soccerIds de los futbolistas : List<ObjectId>
                List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

                List<String> errores = validateContestEntry(aContest, formation, idsList, /*changeInLiveAllowed*/ false);

                if (errores.isEmpty()) {
                    if (MoneyUtils.isGreaterThan(aContest.entryFee, MoneyUtils.zero) &&
                        aContest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                        Money moneyNeeded = Money.zero(MoneyUtils.CURRENCY_GOLD);

                        // Averiguar cuánto dinero ha usado para comprar futbolistas de nivel superior
                        Money managerBalance = User.calculateManagerBalance(theUser.userId);
                        List<InstanceSoccerPlayer> soccerPlayers = aContest.getInstanceSoccerPlayers(idsList);
                        List<InstanceSoccerPlayer> playersToBuy = contestEntry.playersNotPurchased(User.playersToBuy(managerBalance, soccerPlayers));
                        if (!playersToBuy.isEmpty()) {
                            moneyNeeded = moneyNeeded.plus(User.moneyToBuy(aContest, managerBalance, playersToBuy));
                            Logger.debug("editContestEntry: moneyNeeded: {}", moneyNeeded.toString());

                            // Verificar que el usuario tiene dinero suficiente...
                            if (!User.hasMoney(theUser.userId, moneyNeeded)) {
                                errores.add(ERROR_USER_BALANCE_NEGATIVE);
                            }
                        }
                    }
                }

                if (errores.isEmpty()) {
                    if (!ContestEntry.update(theUser, aContest, contestEntry, formation, idsList)) {
                        errores.add(ERROR_RETRY_OP);
                    }
                }

                // TODO: ¿Queremos informar de los distintos errores?
                for (String error : errores) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
                }
            }
            else {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
            }
        }

        Object result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = ImmutableMap.of(
                    "result", "ok",
                    "profile", theUser.getProfile());
        }
        else {
            Logger.error("WTF 7240: editContestEntry: {}", contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }


    public static class CancelContestEntryParams {
        @Constraints.Required
        public String contestEntryId;
    }

    @UserAuthenticated
    public static Result cancelContestEntry() {
        Form<CancelContestEntryParams> contestEntryForm = form(CancelContestEntryParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!contestEntryForm.hasErrors()) {
            CancelContestEntryParams params = contestEntryForm.get();

            Logger.info("cancelContestEntry: contestEntryId({})", params.contestEntryId);

            // Verificar que es un contestEntry válido
            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                // Verificar que el usuario propietario del fantasyTeam sea el mismo que lo intenta borrar
                if (!contestEntry.userId.equals(theUser.userId)) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_OP_UNAUTHORIZED);
                }

                Contest contest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);

                // Verificar que el contest sigue estando activo (ni "live" ni "history")
                if (!contest.state.isActive()) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_NOT_ACTIVE);
                }

                if (!contestEntryForm.hasErrors()) {
                    CancelContestEntryJob cancelContestEntryJob = CancelContestEntryJob.create(theUser.userId, contest.contestId, contestEntry.contestEntryId);
                    if (!cancelContestEntryJob.isDone()) {
                        contestEntryForm.reject(ERROR_RETRY_OP);
                    }
                }
            }
            else {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
            }
        }

        Object result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = ImmutableMap.of(
                    "result", "ok",
                    "profile", theUser.getProfile());
        }
        else {
            Logger.error("WTF 7241: cancelContestEntry: {}", contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    public static class ChangeSoccerPlayerParams {
        @Constraints.Required
        public String contestEntryId;

        @Constraints.Required
        public String soccerPlayerId;

        @Constraints.Required
        public String soccerPlayerIdNew;
    }

    @UserAuthenticated
    public static Result changeSoccerPlayer() {
        Form<ChangeSoccerPlayerParams> changeForm = form(ChangeSoccerPlayerParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        ObjectId contestId = null;

        if (!changeForm.hasErrors()) {
            ChangeSoccerPlayerParams params = changeForm.get();

            Logger.info("changeSoccerPlayer: contestEntryId({}) soccerPlayer: {} -> {}", params.contestEntryId, params.soccerPlayerId, params.soccerPlayerIdNew);

            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                List<String> errores = new ArrayList<>();

                if (!contestEntry.userId.equals(theUser.userId)) {
                    errores.add(ERROR_CONTEST_ENTRY_INVALID);
                }

                // Obtener el contestId : ObjectId
                Contest aContest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);
                ObjectId oldSoccerPlayerId = new ObjectId(params.soccerPlayerId);
                ObjectId newSoccerPlayerId = new ObjectId(params.soccerPlayerIdNew);

                Money moneyNeeded = Money.zero(MoneyUtils.CURRENCY_GOLD);

                if (errores.isEmpty()) {
                    if (contestEntry.containsSoccerPlayer(oldSoccerPlayerId) && !contestEntry.containsSoccerPlayer(newSoccerPlayerId)) {
                        moneyNeeded = moneyNeeded.plus(contestEntry.changeSoccerPlayer(oldSoccerPlayerId, newSoccerPlayerId));
                    } else {
                        errores.add(ERROR_CONTEST_ENTRY_INVALID);
                    }
                }

                if (errores.isEmpty()) {
                    List<TemplateMatchEvent> templateMatchEvents = aContest.getTemplateMatchEvents();

                    // Comprobar que el equipo del futbolista que vamos a sustituir no ha terminado de jugar
                    InstanceSoccerPlayer oldInstanceSoccerPlayer = aContest.getInstanceSoccerPlayer(oldSoccerPlayerId);
                    for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
                        if (templateMatchEvent.containsTemplateSoccerTeam(oldInstanceSoccerPlayer.templateSoccerTeamId)) {
                            if (templateMatchEvent.isGameFinished()) {
                                errores.add(ERROR_SOURCE_SOCCERPLAYER_INVALID);
                            }
                            break;
                        }
                    }

                    // Comprobar que el equipo del futbolista que vamos a introducir no ha comenzado a jugar
                    InstanceSoccerPlayer newInstanceSoccerPlayer = aContest.getInstanceSoccerPlayer(newSoccerPlayerId);
                    for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
                        if (templateMatchEvent.containsTemplateSoccerTeam(newInstanceSoccerPlayer.templateSoccerTeamId)) {
                            if (templateMatchEvent.isGameStarted()) {
                                errores.add(ERROR_TARGET_SOCCERPLAYER_INVALID);
                            }
                            break;
                        }
                    }
                }

                if (errores.isEmpty()) {
                    // Obtener los soccerIds de los futbolistas : List<ObjectId>
                    errores = validateContestEntry(aContest, contestEntry.formation, contestEntry.soccerIds, /*changeInLiveAllowed*/ true);

                    if (errores.isEmpty()) {
                        if (MoneyUtils.isGreaterThan(aContest.entryFee, MoneyUtils.zero) &&
                                aContest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {

                            InstanceSoccerPlayer instanceSoccerPlayer = aContest.getInstanceSoccerPlayer(newSoccerPlayerId);

                            Money managerBalance = User.calculateManagerBalance(theUser.userId);
                            float managerLevel = User.managerLevelFromPoints(managerBalance);

                            moneyNeeded = moneyNeeded.plus(TemplateSoccerPlayer.moneyToBuy(aContest, TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary), (int) managerLevel));

                            if (moneyNeeded.isPositive()) {
                                Logger.debug("changeSoccerPlayer: moneyNeeded: {}", moneyNeeded.toString());

                                // Verificar que el usuario tiene dinero suficiente...
                                if (!User.hasMoney(theUser.userId, moneyNeeded)) {
                                    errores.add(ERROR_USER_BALANCE_NEGATIVE);
                                }
                            }
                        }
                    }
                }

                if (errores.isEmpty()) {
                    contestId = aContest.contestId;
                    if (!ContestEntry.change(theUser, aContest, contestEntry.contestEntryId, oldSoccerPlayerId, newSoccerPlayerId)) {
                        errores.add(ERROR_RETRY_OP);
                    }
                }

                // TODO: ¿Queremos informar de los distintos errores?
                for (String error : errores) {
                    changeForm.reject(CONTEST_ENTRY_KEY, error);
                }
            }
            else {
                changeForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
            }
        }

        Object result = changeForm.errorsAsJson();

        if (!changeForm.hasErrors()) {
            /*
            result = ImmutableMap.of(
                    "result", "ok",
                    "profile", theUser.getProfile());
            */
            return ContestController.getViewContest(contestId.toString());
        }
        else {
            Logger.error("WTF 7249: changeSoccerPlayer: {}", changeForm.errorsAsJson());
        }
        return new ReturnHelper(!changeForm.hasErrors(), result).toResult();
    }

    public static Result generateLineup(String contestId, String formation) {
        boolean result = ContestEntry.FORMATIONS.stream().anyMatch( value -> value.equals(formation) );
        if (!result) {
            return new ReturnHelper(false, ERROR_FORMATION_INVALID).toResult();
        }

        Contest contest = Contest.findOne(contestId);
        if (contest == null) {
            return new ReturnHelper(false, ERROR_CONTEST_INVALID).toResult();
        }

        // Debemos hacer to.do nuestro proceso con los datos de salario, equipo, etc que vienen en los Instances y no en los Templates.
        List<InstanceSoccerPlayer> instanceSoccerPlayers = contest.instanceSoccerPlayers;
        List<TemplateSoccerPlayer> soccerPlayers = TemplateSoccerPlayer.findAllFromInstances(contest.instanceSoccerPlayers);

        // Simplemente "parcheamos" los templates con los datos de los instances
        GenerateLineup.copyInstancesToTemplates(instanceSoccerPlayers, soccerPlayers);

        List<TemplateSoccerPlayer> lineup = GenerateLineup.quickAndDirty(soccerPlayers, contest.salaryCap, formation);

        // Verificar que se haya generado un lineup correcto
        if (lineup.size() != 11 || GenerateLineup.sumSalary(lineup) > contest.salaryCap) {
            Logger.warn("Se tuvo que recurrir a sillyWay, {} contestId, {} players, {} salary", contest.contestId, lineup.size(), GenerateLineup.sumSalary(lineup));
            lineup = GenerateLineup.sillyWay(soccerPlayers, contest.salaryCap, formation);
        }

        return new ReturnHelper(ImmutableMap.builder()
                .put("lineup", lineup)
                .build())
                .toResult(JsonViews.Public.class);
    }

    private static List<String> validateContestEntry (Contest contest, String formation, List<ObjectId> objectIds, boolean changeInLiveAllowed) {
        List<String> errores = new ArrayList<>();

        // Verificar que el contest sea válido
        if (contest == null) {
            errores.add(ERROR_CONTEST_INVALID);
        }
        else {
            // Verificar que el contest esté activo (ni "live" ni "history")
            if (!changeInLiveAllowed && !contest.state.isActive() && !contest.state.isWaitingAuthor()) {
                errores.add(ERROR_CONTEST_NOT_ACTIVE);
            }

            // Buscar los soccerPlayers dentro de los partidos del contest
            List<InstanceSoccerPlayer> soccerPlayers = contest.getInstanceSoccerPlayers(objectIds);

            // Verificar que TODOS los futbolistas seleccionados participen en los partidos del contest
            if (objectIds.size() != soccerPlayers.size()) {
                // No hemos podido encontrar todos los futbolistas referenciados por el contest entry
                errores.add(ERROR_FANTASY_TEAM_INCOMPLETE);
            }
            else {
                // Verificar que los futbolistas no cuestan más que el salaryCap del contest
                if (getSalaryCap(soccerPlayers) > contest.salaryCap) {
                    errores.add(ERROR_SALARYCAP_INVALID);
                }

                // Verificar que todos las posiciones del team están completas
                if (!isFormationValid(formation, soccerPlayers)) {
                    errores.add(ERROR_FORMATION_INVALID);
                }

                // Verificar que no se han incluido muchos players del mismo equipo
                if (!isMaxPlayersFromSameTeamValid(contest, soccerPlayers)) {
                    errores.add(ERROR_MAX_PLAYERS_SAME_TEAM);
                }
            }
        }

        return errores;
    }

    private static int getSalaryCap(List<InstanceSoccerPlayer> soccerPlayers) {
        int salaryCapTeam = 0;
        for (InstanceSoccerPlayer soccer : soccerPlayers) {
            salaryCapTeam += soccer.salary;
        }
        return salaryCapTeam;
    }

    private static boolean isFormationValid(String formation, List<InstanceSoccerPlayer> soccerPlayers) {
        boolean result = ContestEntry.FORMATIONS.stream().anyMatch( value -> value.equals(formation) );
        if (result) {
            int defenses = Character.getNumericValue(formation.charAt(0));
            int middles = Character.getNumericValue(formation.charAt(1));
            int forwards = Character.getNumericValue(formation.charAt(2));
            // Logger.debug("defenses: {} middles: {} forward: {}", defenses, middles, forwards);

            result = (countFieldPos(FieldPos.GOALKEEPER, soccerPlayers) == 1) &&
                    (countFieldPos(FieldPos.DEFENSE, soccerPlayers) == defenses) &&
                    (countFieldPos(FieldPos.MIDDLE, soccerPlayers) == middles) &&
                    (countFieldPos(FieldPos.FORWARD, soccerPlayers) == forwards);
        }
        return result;
    }

    private static boolean isMaxPlayersFromSameTeamValid(Contest contest, List<InstanceSoccerPlayer> soccerPlayers) {
        boolean valid = true;

        Map<String, Integer> numPlayersFromTeam = new HashMap<>();
        for (InstanceSoccerPlayer player : soccerPlayers) {
            String key = player.templateSoccerTeamId.toString();
            Integer num = numPlayersFromTeam.containsKey(key)
                    ? numPlayersFromTeam.get(key)
                    : 0;
            if (num < contest.getMaxPlayersFromSameTeam()) {
                numPlayersFromTeam.put(key, num + 1);
            }
            else {
                valid = false;
                break;
            }
        }

        return valid;
    }

    private static int countFieldPos(FieldPos fieldPos, List<InstanceSoccerPlayer> soccerPlayers) {
        int count = 0;
        for (InstanceSoccerPlayer soccerPlayer : soccerPlayers) {
            if (soccerPlayer.fieldPos.equals(fieldPos)) {
                count++;
            }
        }
        return count;
    }
}
