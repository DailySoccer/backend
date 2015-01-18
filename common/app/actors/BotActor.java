package actors;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import model.*;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import utils.ListUtils;
import utils.ObjectIdMapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BotActor extends UntypedActor {

    @Override public void postRestart(Throwable reason) throws Exception {
        Logger.debug("BotActor postRestart, reason:", reason);
        super.postRestart(reason);

        // Hemos muerto por algun motivo, retickeamos
        getSelf().tell("OnTick", getSelf());
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            // En el caso del SimulatorTick no tenemos que reeschedulear el mensaje porque es el Simulator el que se
            // encarga de drivearnos.
            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    // TODO: Restarts, incluidos los timeouts
    //       Identificacion universal univoca
    //       Multples bots
    //       URL de llamada al server
    //       Nums aleatorios
    //       http://en.wikipedia.org/wiki/Knapsack_problem

    private void onTick() {
        Logger.info("Bot Actor onTick {}", GlobalDate.getCurrentDateString());

        // Vemos si tenemos nuestro usuario, si no, lo preparamos
        if (!login()) {
            signup();

            if (!login()) {
                throw new RuntimeException("WTF 5466");
            }
        }

        // Vemos los concursos activos que tenemos, escogemos el primero que este menos del X% lleno y nos metemos
        for (Contest contest : getActiveContests()) {
            if (!contest.isFull()) {
                enterContest(contest);
            }
        }
    }

    private boolean login() {
        String url = "http://localhost:9000/login";
        JsonNode jsonNode = post(url, String.format("email=%s&password=uoyeradiputs3991", getEmail()));
        return jsonNode.findPath("sessionToken") != null;
    }

    private void signup() {
        String url = "http://localhost:9000/signup";
        JsonNode jsonNode = post(url, String.format("firstName=Bototron&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                    getLastName(), getNickName(), getEmail()));

        Logger.info("Signup returned: {}", jsonNode.toString());
    }

    private void enterContest(Contest contest) {
        String url = String.format("http://localhost:9000/get_public_contest/%s", contest.contestId);
        JsonNode jsonNode = get(url);

        List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.findValue("soccer_players").toString(),
                                                            new TypeReference<List<TemplateSoccerPlayer>>() {});

        List<TemplateSoccerPlayer> lineup = generateLineup(soccerPlayers, contest.salaryCap);
        /*
        for (TemplateSoccerPlayer sp : goalkeepers) {
            Logger.info("{} {} {}", sp.name, sp.salary, sp.fantasyPoints);
        }
        */
    }

    private List<TemplateSoccerPlayer> generateLineup(List<TemplateSoccerPlayer> soccerPlayers, int salaryCap) {
        List<TemplateSoccerPlayer> lineup = new ArrayList<>();

        sortByFantasyPoints(soccerPlayers);

        List<TemplateSoccerPlayer> forwards = filterByPosition(soccerPlayers, FieldPos.FORWARD);
        List<TemplateSoccerPlayer> goalkeepers = filterByPosition(soccerPlayers, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> middles = filterByPosition(soccerPlayers, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> defenses = filterByPosition(soccerPlayers, FieldPos.DEFENSE);

        // Dos delanteros entre los 8 mejores
        for (int c = 0; c < 2; ++c) {
            int next = _rand.nextInt(8);
            lineup.add(forwards.get(next));
        }

        // Un portero de la mitad para abajo
        lineup.add(goalkeepers.get(_rand.nextInt(goalkeepers.size() / 2) + (goalkeepers.size() / 2)));

        // Medios y defensas repartidos por igual, buscamos varias veces partiendo desde la media y aumentado de 100 en
        // 100 cuanto por debajo admitimos su salario
        int averageRemainingSalary = (salaryCap - calcSalaryForLineup(lineup)) / 8;
        int diff = -1;

        for (int tryCounter = 0; tryCounter < 10; ++tryCounter) {
            List<TemplateSoccerPlayer> tempLineup = new ArrayList<>(lineup);

            int maxSal = averageRemainingSalary + 1000;
            int minSal = averageRemainingSalary - (tryCounter*100);
            List<TemplateSoccerPlayer> middlesBySalary = filterBySalary(middles, minSal, maxSal);
            List<TemplateSoccerPlayer> defensesBySalary = filterBySalary(defenses, minSal, maxSal);

            if (middlesBySalary.size() < 4 || defensesBySalary.size() < 4) {
                continue;
            }

            for (int c = 0; c < 4; ++c) {
                int next = _rand.nextInt(Math.min(8, middlesBySalary.size()));
                tempLineup.add(middlesBySalary.get(next));
                next = _rand.nextInt(Math.min(8, defensesBySalary.size()));
                tempLineup.add(defensesBySalary.get(next));
            }

            diff = salaryCap - calcSalaryForLineup(tempLineup);
            Logger.info("Count {} diff {}", tempLineup.size(), diff);

            if (tempLineup.size() == 11 && diff >= 0) {
                lineup = tempLineup;
                break;
            }
        }

        return lineup;
    }

    private int calcSalaryForLineup(List<TemplateSoccerPlayer> sps) {
        int ret = 0;
        for (TemplateSoccerPlayer sp : sps) {
         ret += sp.salary;
        }
        return ret;
    }

    private List<TemplateSoccerPlayer> filterByPosition(List<TemplateSoccerPlayer> sps, final FieldPos fp) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(@Nullable TemplateSoccerPlayer templateSoccerPlayer) {
                return (templateSoccerPlayer != null && templateSoccerPlayer.fieldPos == fp);
            }
        }));
    }

    private List<TemplateSoccerPlayer> filterBySalary(List<TemplateSoccerPlayer> sps, final int salMin, final int salMax) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(@Nullable TemplateSoccerPlayer templateSoccerPlayer) {
                return (templateSoccerPlayer != null &&
                        templateSoccerPlayer.salary >= salMin && templateSoccerPlayer.salary <= salMax);
            }
        }));
    }

    private void sortByFantasyPoints(List<TemplateSoccerPlayer> sps) {
        Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                return o1.fantasyPoints - o2.fantasyPoints;
            }
        });
    }

    private List<Contest> getActiveContests() {
        String url = "http://localhost:9000/get_active_contests";
        JsonNode jsonNode = get(url);
        return fromJSON(jsonNode.findValue("contests").toString(), new TypeReference<List<Contest>>() {});
    }

    private JsonNode post(String url, String params) {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.setContentType("application/x-www-form-urlencoded").post(params);

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        return response.asJson();
                    }
                }
        );
        return jsonPromise.get(10000, TimeUnit.MILLISECONDS);
    }

    private JsonNode get(String url) {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.get();

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        return response.asJson();
                    }
                }
        );
        return jsonPromise.get(10000, TimeUnit.MILLISECONDS);
    }

    private static <T> T fromJSON(final String json, final TypeReference<T> type) {
        T ret = null;

        try {
            // TODO: Ximo fixear numEntries / getNumEntries
            ret = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                    .readValue(json, type);
        } catch (Exception exc) {
            Logger.debug("WTF 2229", exc);
        }
        return ret;
    }

    private String getLastName() {
        return "v1";
    }

    private String getNickName() {
        return "TODO";
    }

    private String getEmail() {
        return "bototron0001@test.com";
    }

    static Random _rand = new Random(91234);
}
