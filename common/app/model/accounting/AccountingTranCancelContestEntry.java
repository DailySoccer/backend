package model.accounting;


import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranCancelContestEntry extends AccountingTran {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingTranCancelContestEntry() {}

    public AccountingTranCancelContestEntry(String currencyCode, ObjectId contestId, ObjectId contestEntryId) {
        super(currencyCode, TransactionType.CANCEL_CONTEST_ENTRY);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingTranCancelContestEntry findOne (ObjectId contestId, ObjectId contestEntryId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #, contestEntryId: #}", TransactionType.CANCEL_CONTEST_ENTRY, contestId, contestEntryId)
                .as(AccountingTranCancelContestEntry.class);
    }

    static public AccountingTran create (String currencyCode, ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingTranCancelContestEntry accountingOp = findOne(contestId, contestEntryId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranCancelContestEntry(currencyCode, contestId, contestEntryId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
