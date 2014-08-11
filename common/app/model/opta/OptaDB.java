package model.opta;


import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.Map;

public class OptaDB {

    @Id
    public ObjectId optaDBId;
    public String xml;
    public String name;
    public DBObject json;
    public Map<String, String[]> headers;
    public long startDate;
    public long endDate;

    public OptaDB(String xml, DBObject json, String name, Map<String, String[]> headers, long startDate, long endDate) {
        this.xml = xml;
        this.json = json;
        this.name = name;
        this.headers = headers;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public OptaDB() {}

    public String getFeedType(){
        String feedType = null;
        if (this.headers.containsKey("X-Meta-Feed-Type")) {
            feedType = this.headers.get("X-Meta-Feed-Type")[0];
        }
        else if (this.headers.containsKey("x-meta-feed-type")) {
            feedType = this.headers.get("x-meta-feed-type")[0];
        }
        return feedType;
    }
}
