package controllers.admin;

import model.GlobalDate;
import model.Model;
import model.PrizeType;
import model.TemplateContest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class TestController extends Controller {

    static public Result start() {
        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().reset();
        return ok("OK");
    }


    static public Result gotoDateTest(int year, int month, int day, int hour, int minute) {

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        Date myDate = new DateTime(year, month, day, hour, minute, DateTimeZone.UTC).toDate();

        OptaSimulator.instance().gotoDate(myDate);

        return ok("OK");
    }


    static public Result gotoDate(Long timestamp) {
        Date date = new Date(timestamp);

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().gotoDate(date);
        return ok("OK");
    }

    static public Result initialSetup() {
        DashboardController.initialSetup();
        return ok("OK");
    }

    static public Result getCurrentDate() {
        return ok(GlobalDate.getCurrentDateString());
    }

    static public Result createContests(int mockIndex){
        TemplateContest templateContest;

        switch(mockIndex) {
            case 0:
                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.!! %MockUsers", 1, 200, 60000, 0, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new ArrayList<String>(Arrays.asList("731782", "731768"))) //RUS-SOUTHKOREA MEX-CMR
                );

                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.... %MockUsers", 1, 200, 70000, 0, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0, DateTimeZone.UTC).toDate(),
                        new ArrayList<String>(Arrays.asList("731767", "731776", "731793", "731813"))) // BRA-HRV FRA-HND ARG-IRN KOR-BEL
                );

                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.++ %MockUsers", 1, 100, 65000, 0, PrizeType.FREE,
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

}
