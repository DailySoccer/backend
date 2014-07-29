package controllers.admin;

import model.Snapshot;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;

public class SnapshotController extends Controller {

    public static Result index() {
        return ok(views.html.snapshot.render());
    }

    public static Result replayLast() {
        OptaSimulator.reset();
        OptaSimulator.useSnapshot();
        return redirect(routes.SnapshotController.index());
    }

    public static Result continueSnapshot() {
        Snapshot.load();
        OptaSimulator.resetInstance();

        return redirect(routes.SnapshotController.index());
    }

    public static Result snapshot() {
        Snapshot.create();

        return redirect(routes.SnapshotController.index());
    }

    public static Result snapshotDB() {
        Snapshot.createInDB();

        return redirect(routes.SnapshotController.index());
    }

    public static Result snapshotDump() {
        ProcessBuilder pb = new ProcessBuilder("./snapshot_dump.sh", "snapshot000");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 4264", e);
        }
        return redirect(routes.SnapshotController.index());
    }

    public static Result snapshotRestore() {
        ProcessBuilder pb = new ProcessBuilder("./snapshot_restore.sh", "snapshot000");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 1124", e);
        }
        return redirect(routes.SnapshotController.index());
    }

    public static Result exportSalaries() {
        ProcessBuilder pb = new ProcessBuilder("./export_salaries.sh", "salaries.csv");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 1124", e);
        }
        return redirect(routes.SnapshotController.index());
    }

    public static Result importSalaries() {
        ProcessBuilder pb = new ProcessBuilder("./import_salaries.sh", "salaries.csv");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 1124", e);
        }
        return redirect(routes.SnapshotController.index());
    }

    public static Result importSalariesServer() {
        ProcessBuilder pb = new ProcessBuilder("./import_salaries_server.sh", "salaries.csv");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 1124", e);
        }
        return redirect(routes.SnapshotController.index());
    }
}
