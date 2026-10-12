package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;

/** One-time withdrawal of proven erroneous DPPMS events after complete formal history carry. */
public interface DppmsSoftwareLegacyEventWithdrawalApi {
    List<Baseline> preview(DppmsAssetSourceHistoryApi.Command source, List<Identity> identities);
    void withdraw(Withdrawal command);
    void verifyWithdrawn(DppmsAssetSourceHistoryApi.Command source, List<Identity> identities);
    record Identity(Long sourceId, Long eventId, Long recordId) {}
    record Baseline(Identity identity, String fieldsJson) {}
    record Withdrawal(DppmsAssetSourceHistoryApi.Command source, List<Baseline> baselines) {}
}
