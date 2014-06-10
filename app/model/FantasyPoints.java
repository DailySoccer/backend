package model;

import org.bson.types.ObjectId;

import java.util.Date;

/**
 * Created by gnufede on 04/06/14.
 */
public class FantasyPoints {
    public int points;
    public int eventType;
    public String playerId;
    public ObjectId eventId;
    public ObjectId pointsTranslationId;
    public Date timestamp;
    public long unixtimestamp;
}
