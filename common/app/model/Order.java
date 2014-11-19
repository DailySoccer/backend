package model;

import com.google.gson.JsonObject;
import org.jongo.marshall.jackson.oid.Id;
import org.bson.types.ObjectId;

public class Order {
    static final String REFERER_URL_DEFAULT = "epiceleven.com";

    public enum TransactionType {
        PAYPAL
    }

    public enum State {
        WAITING_APPROVAL,
        WAITING_PAYMENT,
        PENDING,
        COMPLETED,
        CANCELED
    }

    @Id
    public ObjectId orderId;

    public TransactionType transactionType;
    public ObjectId userId;
    public State state;
    public String reason;

    public String paymentId;
    public String payerId;
    public String referer;

    public Order() {
    }

    public Order(ObjectId orderId, ObjectId userId, TransactionType transactionType, String paymentId) {
        this.orderId = orderId;
        this.transactionType = transactionType;
        this.userId = userId;
        this.state = State.WAITING_APPROVAL;
        this.paymentId = paymentId;
    }

    public void setWaitingPayment(String payerId) {
        this.state = State.WAITING_PAYMENT;
        this.payerId = payerId;
        Model.orders().update(orderId).with("{$set: {state: #, payerId: #}}", this.state, this.payerId);
    }

    public void setPending() {
        this.state = State.PENDING;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    public void setCompleted() {
        this.state = State.COMPLETED;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    public void setCanceled() {
        this.state = State.CANCELED;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    static public Order findOne(String orderId) {
        return Model.orders().findOne("{_id : #}", new ObjectId(orderId)).as(Order.class);
    }

    static public Order findOneFromPayment(String paymentId) {
        return Model.orders().findOne("{paymentId : #}", paymentId).as(Order.class);
    }

    static public Order create (ObjectId orderId, ObjectId userId, TransactionType transactionType, String paymentId, String refererUrl) {
        Order order = new Order(orderId, userId, transactionType, paymentId);
        // No almacenamos el referer si es el "por defecto"
        if (!refererUrl.contains(REFERER_URL_DEFAULT)) {
            order.referer = refererUrl;
        }
        Model.orders().insert(order);
        return order;
    }
}
