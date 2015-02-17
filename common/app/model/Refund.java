package model;

import com.google.common.collect.ImmutableList;
import model.accounting.AccountOp;
import model.accounting.AccountingTranRefund;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;

public class Refund {
    public enum State {
        PENDING,
        PROCESSING,
        COMPLETED
    }

    @Id
    public ObjectId refundId;

    public ObjectId userId;
    public State state;
    public Money amount;

    public Refund() {}

    public Refund(ObjectId userId, Money amount) {
        this.userId = userId;
        this.state = State.PENDING;
        this.amount = amount;
    }

    static public Refund findOne(String refundId) {
        return ObjectId.isValid(refundId) ? Model.refunds().findOne("{_id : #}", new ObjectId(refundId)).as(Refund.class) : null;
    }

    public void insert() {
        Model.refunds().insert(this);
    }

    public void apply() {
        if (state.equals(State.PENDING)) {
            setProcessing();
        }

        if (state.equals(State.PROCESSING)) {
            Integer seqId = User.getSeqId(userId) + 1;

            // El usuario tiene dinero suficiente?
            Money userBalance = User.calculateBalance(userId);
            if (userBalance.compareTo(amount) >= 0) {

                // Registrar la devolución
                AccountingTranRefund.create(refundId, ImmutableList.of(
                        new AccountOp(userId, amount.negated(), seqId)
                ));

                setCompleted();
            }
        }
    }

    private void setProcessing() {
        state = State.PROCESSING;
        Model.refunds().update(refundId).with("{$set: {state: #}}", this.state);
    }

    private void setCompleted() {
        state = State.COMPLETED;
        Model.refunds().update(refundId).with("{$set: {state: #}}", this.state);
    }
}
