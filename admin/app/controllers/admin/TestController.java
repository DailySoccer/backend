package controllers.admin;

import actions.CheckTargetEnvironment;
import actors.MessageEnvelope;
import actors.SimulatorState;
import model.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

@CheckTargetEnvironment
public class TestController extends Controller {

    static public Result start() {
        Model.reset(false);
        return ok("OK");
    }


    static public Result gotoDate(int year, int month, int day, int hour, int minute, int second) {
        Date myDate = new DateTime(year, month, day, hour, minute, second, DateTimeZone.UTC).toDate();
        Model.actors().tell("SimulatorActor", new MessageEnvelope("GotoDate", myDate));
        return ok("OK");
    }


    static public Result gotoDateTimestamp(Long timestamp) {
        Date myDate = new Date(timestamp);
        Model.actors().tell("SimulatorActor", new MessageEnvelope("GotoDate", myDate));
        return ok("OK");
    }

    static public Result initialSetup() {
        DashboardController.initialSetup();
        return ok("OK");
    }

    static public Result getCurrentDate() {

        SimulatorState simulatorState = null;

        try {
            simulatorState = Model.actors().tellAndAwait("SimulatorActor", "GetSimulatorState");
        }
        catch (Exception e) {}

        if (simulatorState != null) {
            return ok(simulatorState.getCurrentDateFormatted());
        }
        else {
            return ok(GlobalDate.getCurrentDateString());
        }
    }

    static public Result createContests(int mockIndex){
        TemplateContest templateContest;

        switch(mockIndex) {
            case 0:
                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.!! %MockUsers", 1, 0, 200, SalaryCap.DIFFICULT, MoneyUtils.zero, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new ArrayList<String>(Arrays.asList("731782", "731768"))) //RUS-SOUTHKOREA MEX-CMR
                );

                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.... %MockUsers", 1, 0, 200, SalaryCap.EASY, MoneyUtils.zero, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new ArrayList<String>(Arrays.asList("731767", "731776", "731793", "731813"))) // BRA-HRV FRA-HND ARG-IRN KOR-BEL
                );

                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.++ %MockUsers", 1, 0, 100, SalaryCap.STANDARD, MoneyUtils.zero, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new ArrayList<String>(Arrays.asList("731767", "731768", "731769"))) // BRA-HRV MEX-CMR ESP-NLD
                );

                break;
            case 1:
                break;
            default:
        }


        return ok("OK");
    }


    static public Result createPromos(int mockIndex){

        switch(mockIndex) {
            case 0:
                Model.promos().insert(new Promo(
                        new DateTime(2010, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new DateTime(2020, 7, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        1,
                        "mockpromo1",
                        "#/restricted",
                        "",
                        "images/promos/promoGen01Xs.jpg",
                        "images/promos/promoGen01Desktop.png")
                );
                break;
            case 1:
                Model.promos().insert(new Promo(
                                new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                                new DateTime(2014, 7, 12, 0, 0, DateTimeZone.UTC).toDate(),
                                1,
                                "mockpromo2",
                                "#/restricted",
                                "",
                                "images/promos/promoGen02Xs.jpg",
                                "images/promos/promoGen02Desktop.png")
                );
                break;
            default:
        }


        return ok("OK");
    }

}
