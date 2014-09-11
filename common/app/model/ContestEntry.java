package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ContestEntry implements JongoId {
    @Id
    public ObjectId contestEntryId;
    public ObjectId userId;             // Usuario que creo el equipo

    @JsonView(JsonViews.FullContest.class)
    public List<ObjectId> soccerIds;    // Fantasy team

    @JsonView(JsonViews.FullContest.class)
    public int position = -1;

    @JsonView(JsonViews.FullContest.class)
    public int prize;

    @JsonView(JsonViews.FullContest.class)
    public int fantasyPoints;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, List<ObjectId> soccerIds) {
        this.contestEntryId = new ObjectId();
        this.userId = userId;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestEntryId; }

    public void updateRanking() {
        // Logger.info("ContestEntry: {} | UserId: {} | Position: {} | FantasyPoints: {}", contestEntryId, userId, position, fantasyPoints);

        Model.contests()
            .update("{'contestEntries._id': #}", getId())
            .with("{$set: {'contestEntries.$.position': #, 'contestEntries.$.fantasyPoints': #, 'contestEntries.$.prize': #}}",
                    position, fantasyPoints, prize);
    }

    public void updateRanking(BulkWriteOperation bulkOperation) {
        // Logger.info("ContestEntry: {} | UserId: {} | Position: {} | FantasyPoints: {}", contestEntryId, userId, position, fantasyPoints);

        DBObject query = new BasicDBObject("contestEntries._id", getId());
        DBObject update = new BasicDBObject("$set", new BasicDBObject("contestEntries.$.position", position)
                                                              .append("contestEntries.$.fantasyPoints", fantasyPoints)
                                                              .append("contestEntries.$.prize", prize));
        bulkOperation.find(query).updateOne(update);
    }

    static public ContestEntry findOne(String contestId) {
        ContestEntry aContestEntry = null;
        if (ObjectId.isValid(contestId)) {
            aContestEntry = findOne(new ObjectId(contestId));
        }
        return aContestEntry;
    }

    static public ContestEntry findOne(ObjectId contestEntryId) {
        Contest contest = Contest.findOneFromContestEntry(contestEntryId);

        ContestEntry contestEntry = null;

        for (ContestEntry entry : contest.contestEntries) {
            if (entry.contestEntryId.equals(contestEntryId)) {
                contestEntry = entry;
                break;
            }
        }

        return contestEntry;
    }

    static public List<ContestEntry> findAllFromContests() {
        List<ContestEntry> contestEntries = new ArrayList<>();

        List<Contest> contests = ListUtils.asList(Model.contests().find().as(Contest.class));
        for (Contest contest : contests) {
            contestEntries.addAll(contest.contestEntries);
        }

        return contestEntries;
    }

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contestId   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean create(ObjectId user, ObjectId contestId, List<ObjectId> soccers) {

        boolean bRet = false;

        try {
            Contest contest = Model.contests().findOne("{ _id: # }", contestId).as(Contest.class);
            if (contest != null) {
                ContestEntry aContestEntry = new ContestEntry(user, soccers);
                contest.contestEntries.add(aContestEntry);
                Model.contests().update(contest.contestId).with(contest);

                bRet = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 2032: ", exc);
        }

        return bRet;
    }

    public static boolean remove(ObjectId contestId, ObjectId contestEntryId) {

        boolean bRet = false;

        try {
            Contest contest = Model.contests().findOne("{ _id: # }", contestId).as(Contest.class);
            if (contest != null) {
                ContestEntry contestToRemove = contest.findContestEntry(contestEntryId);
                if (contestToRemove != null) {
                    contest.contestEntries.remove(contestToRemove);
                    Model.contests().update(contest.contestId).with(contest);
                }
                // TODO: Más rápido pero algo más peligroso (hemos de mantener "numEntries" actualizado)
                // Model.contests().update("{ contestEntries._id: # }", contestEntryId).with("{ $pull: { contestEntries: { _id: # } }, $inc: { numEntries: -1 } }", contestEntryId);

                bRet = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 7801: ", exc);
        }

        return bRet;
    }

    public int getFantasyPointsFromMatchEvents(List<MatchEvent> matchEvents) {
        int fantasyPoints = 0;
        for (ObjectId templateSoccerPlayerId : soccerIds) {
            for (MatchEvent matchEvent : matchEvents) {
                if (matchEvent.containsSoccerPlayer(templateSoccerPlayerId)) {
                    fantasyPoints += matchEvent.getFantasyPoints(templateSoccerPlayerId);
                    break;
                }
            }
        }
        return fantasyPoints;
    }
}
