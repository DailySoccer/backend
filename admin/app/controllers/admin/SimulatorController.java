package controllers.admin;

import model.GlobalDate;
import model.Snapshot;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Date;

import static play.data.Form.form;

public class SimulatorController extends Controller {

    public static Result index() {
        return ok(views.html.simulator.render());
    }

    public static Result currentDate() {
        return ok(GlobalDate.getCurrentDateString());
    }

    public static Result start() {

        boolean wasResumed = OptaSimulator.start();

        return ok();
    }

    public static Result pause() {
        OptaSimulator.pause();
        return ok();
    }

    public static Result nextStep() {
        OptaSimulator.nextStep();
        return ok();
    }

    public static Result isRunning() {
        return ok(((Boolean)!OptaSimulator.isFinished()).toString());
    }

    public static Result isPaused() {
        return ok(((Boolean)OptaSimulator.isPaused()).toString());
    }

    public static Result getNextStop() {
        return ok(OptaSimulator.getNextStop());
    }

    public static Result nextStepDescription() {
        return ok(OptaSimulator.getNextStepDescription());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoDate() {

        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();

        GotoSimParams params = gotoForm.get();
        OptaSimulator.gotoDate(params.date);
        OptaSimulator.start();

        return ok();
    }

    public static Result reset(){
        OptaSimulator.reset();
        return ok();
    }

    public static Result replayLast() {
        OptaSimulator.reset();
        OptaSimulator.useSnapshot( Snapshot.getLast() );

        return redirect(routes.SimulatorController.index());
    }

    public static Result snapshot() {
        Snapshot.create();

        return redirect(routes.SimulatorController.index());
    }

    public static Result snapshotDB() {
        Snapshot.createInDB();

        return redirect(routes.SimulatorController.index());
    }
}
