package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class User {
    @Id
    public ObjectId userId;

	public String firstName;
	public String lastName;
    public String nickName;
	public String email;

    @JsonView(JsonViews.NotForClient.class)
	public String password;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public User() {
    }

	public User(String firstName, String lastName, String nickName, String email, String password) {
		this.firstName = firstName;
		this.lastName = lastName;
        this.nickName = nickName;
		this.email = email;
		this.password = password;
        createdAt = GlobalDate.getCurrentDate();
	}

    public UserInfo info() {
        return new UserInfo(userId, firstName, lastName, nickName);
    }

    /**
     * Query de un usuario por su identificador en mongoDB (verifica la validez del mismo)
     *
     * @param userId Identificador del usuario
     * @return User
     */
    static public User find(String userId) {
        User aUser = null;
        Boolean userValid = ObjectId.isValid(userId);
        if (userValid) {
            aUser = Model.users().findOne(new ObjectId(userId)).as(User.class);
        }
        return aUser;
    }

    static public List<User> find(List<ContestEntry> contestEntries) {
        return ListUtils.asList(Model.findObjectIds(Model.users(), "_id", ListUtils.convertToIdList(contestEntries)).as(User.class));
    }


    /*
    @JsonView(JsonViews.NotForClient.class)
    public ObjectId _id;
    */
}