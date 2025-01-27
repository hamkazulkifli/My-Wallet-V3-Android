package piuk.blockchain.android.sunriver

import com.blockchain.kycui.sunriver.SunriverCampaignHelper
import com.blockchain.sunriver.XlmDataManager
import info.blockchain.balance.AccountReference
import io.reactivex.Single

internal class SunRiverCampaignAccountProviderAdapter(
    private val xlmDataManager: XlmDataManager
) : SunriverCampaignHelper.XlmAccountProvider {

    override fun defaultAccount(): Single<AccountReference.Xlm> = xlmDataManager.defaultAccount()
}
