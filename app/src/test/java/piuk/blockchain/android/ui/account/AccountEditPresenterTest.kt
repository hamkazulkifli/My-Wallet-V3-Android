package piuk.blockchain.android.ui.account

import android.annotation.SuppressLint
import android.content.Intent
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.LegacyAddress
import info.blockchain.wallet.payload.data.Options
import info.blockchain.wallet.payload.data.Wallet
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.Completable
import io.reactivex.Observable
import org.amshove.kluent.any
import org.apache.commons.lang3.tuple.Pair
import org.bitcoinj.core.Address
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.BitcoinMainNetParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.data.cache.DynamicFeeCache
import piuk.blockchain.android.ui.account.AccountEditActivity.Companion.EXTRA_ACCOUNT_INDEX
import piuk.blockchain.android.ui.account.AccountEditActivity.Companion.EXTRA_CRYPTOCURRENCY
import piuk.blockchain.android.ui.send.PendingTransaction
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceiveHelper
import piuk.blockchain.android.ui.zxing.CaptureActivity
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.currency.CurrencyFormatManager
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import java.math.BigInteger
import java.util.ArrayList
import java.util.Arrays

@Config(
    sdk = [23],
    constants = BuildConfig::class,
    application = BlockchainTestApplication::class
)
@RunWith(RobolectricTestRunner::class)
class AccountEditPresenterTest {

    private lateinit var subject: AccountEditPresenter
    private val view: AccountEditView = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val bchDataManager: BchDataManager = mock()
    private val metadataManager: MetadataManager = mock()
    private val prefsUtil: PrefsUtil = mock()
    private val stringUtils: StringUtils = mock()
    private val accountEditModel: AccountEditModel = mock()
    private val swipeToReceiveHelper: SwipeToReceiveHelper = mock()
    private val sendDataManager: SendDataManager = mock()
    private val privateKeyFactory: PrivateKeyFactory = mock()
    private val environmentSettings: EnvironmentConfig = mock()
    private val dynamicFeeCache: DynamicFeeCache = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val currencyFormatManager: CurrencyFormatManager = mock()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        subject = AccountEditPresenter(
            prefsUtil,
            stringUtils,
            payloadDataManager,
            bchDataManager,
            metadataManager,
            sendDataManager,
            privateKeyFactory,
            swipeToReceiveHelper,
            dynamicFeeCache,
            environmentSettings,
            currencyFormatManager
        )
        subject.initView(view)
        subject.accountModel = accountEditModel

        whenever(environmentSettings.bitcoinNetworkParameters).thenReturn(BitcoinMainNetParams.get())
    }

    @Test
    fun setAccountModel() {
        // Arrange
        val newModel = AccountEditModel(mock())
        // Act
        subject.accountModel = newModel
        // Assert
        assertEquals(newModel, subject.accountModel)
    }

    @Test
    fun onViewReadyV3() {
        // Arrange
        val intent = Intent().apply {
            putExtra(EXTRA_ACCOUNT_INDEX, 0)
            putExtra(EXTRA_CRYPTOCURRENCY, CryptoCurrency.BTC)
        }
        whenever(view.activityIntent).thenReturn(intent)
        val importedAccount: Account = mock()
        whenever(importedAccount.xpub).thenReturn("")
        val account: Account = mock()
        whenever(account.xpub).thenReturn("")
        whenever(account.label).thenReturn("")
        whenever(payloadDataManager.accounts).thenReturn(listOf(account, importedAccount))
        whenever(payloadDataManager.defaultAccount).thenReturn(mock())
        whenever(stringUtils.getString(anyInt())).thenReturn("string resource")
        // Act
        subject.onViewReady()
        // Assert
        verify(view, atLeastOnce()).activityIntent
        verify(accountEditModel).label = anyString()
        verify(accountEditModel).labelHeader = "string resource"
        verify(accountEditModel).scanPrivateKeyVisibility = anyInt()
        verify(accountEditModel).xpubText = "string resource"
        verify(accountEditModel).transferFundsVisibility = anyInt()
    }

    @Test
    fun onViewReadyV3Archived() {
        // Arrange
        val intent = Intent().apply {
            putExtra(EXTRA_ACCOUNT_INDEX, 0)
            putExtra(EXTRA_CRYPTOCURRENCY, CryptoCurrency.BTC)
        }
        whenever(view.activityIntent).thenReturn(intent)
        val importedAccount: Account = mock()
        whenever(importedAccount.xpub).thenReturn("")
        val account: Account = mock()
        whenever(account.xpub).thenReturn("")
        whenever(account.label).thenReturn("")
        whenever(account.isArchived).thenReturn(true)
        whenever(payloadDataManager.accounts).thenReturn(listOf(account, importedAccount))
        whenever(payloadDataManager.defaultAccount).thenReturn(mock())
        whenever(stringUtils.getString(anyInt())).thenReturn("string resource")
        // Act
        subject.onViewReady()
        // Assert
        verify(view, atLeastOnce()).activityIntent
        verify(accountEditModel).label = anyString()
        verify(accountEditModel).labelHeader = "string resource"
        verify(accountEditModel).scanPrivateKeyVisibility = anyInt()
        verify(accountEditModel).xpubText = "string resource"
        verify(accountEditModel).transferFundsVisibility = anyInt()
    }

    @Test
    fun onClickTransferFundsSuccess() {
        // Arrange
        val intent = Intent().apply {
            putExtra(EXTRA_CRYPTOCURRENCY, CryptoCurrency.BTC)
        }
        whenever(view.activityIntent).thenReturn(intent)
        val legacyAddress = LegacyAddress()
        legacyAddress.address = ""
        legacyAddress.label = ""
        subject.legacyAddress = legacyAddress
        val sweepableCoins = Pair.of(BigInteger.ONE, BigInteger.TEN)
        whenever(dynamicFeeCache.btcFeeOptions!!.regularFee).thenReturn(100L)
        whenever(payloadDataManager.defaultAccount).thenReturn(mock())
        whenever(payloadDataManager.getNextReceiveAddress(any(Account::class)))
            .thenReturn(Observable.just("address"))
        whenever(sendDataManager.getUnspentOutputs(legacyAddress.address))
            .thenReturn(Observable.just(mock()))
        whenever(
            sendDataManager.getMaximumAvailable(
                eq(CryptoCurrency.BTC),
                any(),
                any()
            )
        ).thenReturn(sweepableCoins)
        val spendableUnspentOutputs: SpendableUnspentOutputs = mock()
        whenever(spendableUnspentOutputs.absoluteFee).thenReturn(BigInteger.TEN)
        whenever(spendableUnspentOutputs.consumedAmount).thenReturn(BigInteger.TEN)
        whenever(
            sendDataManager.getSpendableCoins(
                any(),
                any(),
                any()
            )
        ).thenReturn(spendableUnspentOutputs)
        whenever(prefsUtil.getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY))
            .thenReturn("USD")
        whenever(sendDataManager.estimateSize(anyInt(), anyInt())).thenReturn(1337)

        whenever(currencyFormatManager.getFormattedSelectedCoinValue(any()))
            .thenReturn("")

        whenever(currencyFormatManager.getFormattedFiatValueFromSelectedCoinValue(any(), eq(null), eq(null)))
            .thenReturn("")

        whenever(currencyFormatManager.getFiatSymbol(any())).thenReturn("")

        // Act
        subject.onClickTransferFunds()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showPaymentDetails(any(PaymentConfirmationDetails::class))
    }

    @Test
    fun onClickTransferFundsSuccessTransactionEmpty() {
        // Arrange
        val intent = Intent().apply {
            putExtra(EXTRA_CRYPTOCURRENCY, CryptoCurrency.BTC)
        }
        whenever(view.activityIntent).thenReturn(intent)
        val legacyAddress = LegacyAddress()
        legacyAddress.address = ""
        legacyAddress.label = ""
        subject.legacyAddress = legacyAddress
        val sweepableCoins = Pair.of(BigInteger.ZERO, BigInteger.TEN)
        whenever(dynamicFeeCache.btcFeeOptions!!.regularFee).thenReturn(100L)
        whenever(payloadDataManager.defaultAccount).thenReturn(mock())
        whenever(payloadDataManager.getNextReceiveAddress(any(Account::class)))
            .thenReturn(Observable.just("address"))
        whenever(sendDataManager.getUnspentOutputs(legacyAddress.address))
            .thenReturn(Observable.just(mock()))
        whenever(
            sendDataManager.getMaximumAvailable(
                eq(CryptoCurrency.BTC),
                any(),
                any()
            )
        ).thenReturn(sweepableCoins)
        whenever(
            sendDataManager.getSpendableCoins(
                any(),
                any(),
                any()
            )
        ).thenReturn(mock())
        // Act
        subject.onClickTransferFunds()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun onClickTransferFundsError() {
        // Arrange
        val intent = Intent().apply {
            putExtra(EXTRA_CRYPTOCURRENCY, CryptoCurrency.BTC)
        }
        whenever(view.activityIntent).thenReturn(intent)
        val legacyAddress = LegacyAddress()
        legacyAddress.address = ""
        legacyAddress.label = ""
        subject.legacyAddress = legacyAddress
        whenever(sendDataManager.getUnspentOutputs(legacyAddress.address))
            .thenReturn(Observable.error(Throwable()))
        // Act
        subject.onClickTransferFunds()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun transferFundsClickable() {
        // Arrange
        whenever(accountEditModel.transferFundsClickable).thenReturn(false)
        // Act
        val value = subject.transferFundsClickable()
        // Assert
        assertFalse(value)
    }

    @Test
    fun submitPaymentSuccess() {
        // Arrange
        val legacyAddress = LegacyAddress().apply { address = "" }
        val pendingTransaction = PendingTransaction().apply {
            bigIntAmount = BigInteger("1")
            bigIntFee = BigInteger("1")
            sendingObject = ItemAccount("", "", "", null, legacyAddress, null)
            unspentOutputBundle = SpendableUnspentOutputs()
            receivingAddress = ""
        }
        val mockPayload: Wallet = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(payloadDataManager.getAddressECKey(legacyAddress, null))
            .thenReturn(mock())
        whenever(
            sendDataManager.submitBtcPayment(
                any(SpendableUnspentOutputs::class),
                anyList(),
                any(String::class),
                any(String::class),
                any(BigInteger::class),
                any(BigInteger::class)
            )
        ).thenReturn(Observable.just("hash"))
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        subject.pendingTransaction = pendingTransaction
        // Act
        subject.submitPayment()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showTransactionSuccess()
        verify(accountEditModel).transferFundsVisibility = anyInt()
        verify(view).setActivityResult(anyInt())
    }

    @Test
    fun submitPaymentFailed() {
        // Arrange
        val pendingTransaction = PendingTransaction()
        pendingTransaction.bigIntAmount = BigInteger("1")
        pendingTransaction.bigIntFee = BigInteger("1")
        val legacyAddress = LegacyAddress()
        pendingTransaction.sendingObject = ItemAccount("", "", "", null, legacyAddress, null)
        pendingTransaction.unspentOutputBundle = SpendableUnspentOutputs()
        val mockPayload: Wallet = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(payloadDataManager.getAddressECKey(eq(legacyAddress), anyString()))
            .thenReturn(mock())
        whenever(
            sendDataManager.submitBtcPayment(
                any(SpendableUnspentOutputs::class),
                anyList(),
                any(String::class),
                any(String::class),
                any(BigInteger::class),
                any(BigInteger::class)
            )
        ).thenReturn(Observable.error(Throwable()))
        subject.pendingTransaction = pendingTransaction
        // Act
        subject.submitPayment()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun submitPaymentECKeyException() {
        // Arrange
        val pendingTransaction = PendingTransaction()
        pendingTransaction.bigIntAmount = BigInteger("1")
        pendingTransaction.bigIntFee = BigInteger("1")
        val legacyAddress = LegacyAddress()
        pendingTransaction.sendingObject = ItemAccount("", "", "", null, legacyAddress, null)
        val mockPayload: Wallet = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockPayload.isDoubleEncryption).thenReturn(true)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        subject.pendingTransaction = pendingTransaction
        // Act
        subject.submitPayment()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun updateAccountLabelInvalid() {
        // Arrange

        // Act
        subject.updateAccountLabel("    ")
        // Assert
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun updateAccountLabelSuccess() {
        // Arrange
        subject.account = Account()
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.updateAccountLabel("label")
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(accountEditModel).label = anyString()
        verify(view).setActivityResult(anyInt())
    }

    @Test
    fun updateAccountLabelFailed() {
        // Arrange
        subject.legacyAddress = LegacyAddress().apply { label = "old label" }
        whenever(payloadDataManager.syncPayloadWithServer())
            .thenReturn(Completable.error(Throwable()))
        // Act
        subject.updateAccountLabel("new label")
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(accountEditModel).label = "old label"
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun onClickChangeLabel() {
        // Arrange
        whenever(accountEditModel.label).thenReturn("label")
        // Act
        subject.onClickChangeLabel(mock())
        // Assert
        verify(view).promptAccountLabel("label")
    }

    @Test
    fun onClickDefaultSuccess() {
        // Arrange
        val account = Account()
        account.xpub = ""
        subject.account = account
        val mockPayload: Wallet = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.defaultAccountIndex).thenReturn(0)
        whenever(mockPayload.hdWallets[0].accounts)
            .thenReturn(listOf(account))
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        whenever(swipeToReceiveHelper.updateAndStoreBitcoinAddresses())
            .thenReturn(Completable.complete())
        whenever(swipeToReceiveHelper.updateAndStoreBitcoinCashAddresses())
            .thenReturn(Completable.complete())
        // Act
        subject.onClickDefault(mock())
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).setActivityResult(anyInt())
        verify(view).updateAppShortcuts()
    }

    @Test
    fun onClickDefaultFailure() {
        // Arrange
        val account = Account()
        account.xpub = ""
        subject.account = account
        val mockPayload: Wallet = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.defaultAccountIndex).thenReturn(0)
        whenever(mockPayload.hdWallets[0].accounts)
            .thenReturn(listOf(account))
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(payloadDataManager.syncPayloadWithServer())
            .thenReturn(Completable.error(Throwable()))
        // Act
        subject.onClickDefault(mock())
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun onClickScanXpriv() {
        // Arrange
        subject.legacyAddress = LegacyAddress()
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        // Act
        subject.onClickScanXpriv(mock())
        // Assert
        verify(view).startScanActivity()
    }

    @Test
    fun onClickScanXprivDoubleEncrypted() {
        // Arrange
        subject.legacyAddress = LegacyAddress().apply {
            address = "1Address"
        }
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(true)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(
            stringUtils.getFormattedString(
                eq(R.string.watch_only_spend_instructions),
                eq("1Address")
            )
        ).thenReturn("Watch only 1Address")
        // Act
        subject.onClickScanXpriv(mock())
        // Assert
        verify(view).promptPrivateKey(anyString())
    }

    @Test
    fun onClickShowXpubAccount() {
        // Arrange
        subject.account = Account()
        // Act
        subject.onClickShowXpub(mock())
        // Assert
        verify(view).showXpubSharingWarning()
    }

    @Test
    fun onClickShowXpubLegacyAddress() {
        // Arrange
        subject.legacyAddress = LegacyAddress()
        // Act
        subject.onClickShowXpub(mock())
        // Assert
        verify(view).showAddressDetails(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )
    }

    @Test
    fun onClickArchive() {
        // Arrange
        subject.account = Account()
        whenever(stringUtils.getString(anyInt())).thenReturn("resource string")
        // Act
        subject.onClickArchive(mock())
        // Assert
        verify(view).promptArchive("resource string", "resource string")
    }

    @Test
    fun showAddressDetails() {
        // Arrange
        subject.legacyAddress = LegacyAddress()
        // Act
        subject.showAddressDetails()
        // Assert
        verify(view).showAddressDetails(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )
    }

    @Test
    fun handleIncomingScanIntentInvalidData() {
        // Arrange
        val intent = Intent()
        intent.putExtra(CaptureActivity.SCAN_RESULT, null as Array<String>?)
        // Act
        subject.handleIncomingScanIntent(intent)
        // Assert

        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun handleIncomingScanIntentUnrecognisedKeyFormat() {
        // Arrange
        val intent = Intent().apply {
            putExtra(
                CaptureActivity.SCAN_RESULT,
                "6PRJmkckxBct8jUwn6UcJbickdrnXBiPP9JkNW83g4VyFNsfEuxas39pS"
            )
        }
        whenever(privateKeyFactory.getFormat(anyString())).thenReturn(null)
        // Act
        subject.handleIncomingScanIntent(intent)
        // Assert
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun handleIncomingScanIntentBip38() {
        // Arrange
        val intent = Intent().apply {
            putExtra(
                CaptureActivity.SCAN_RESULT,
                "6PRJmkckxBct8jUwn6UcJbickdrnXBiPP9JkNW83g4VyFNsfEuxas39pSS"
            )
        }
        whenever(privateKeyFactory.getFormat(anyString())).thenReturn(PrivateKeyFactory.BIP38)
        // Act
        subject.handleIncomingScanIntent(intent)
        // Assert
        verify(view).promptBIP38Password(anyString())
    }

    @Test
    fun handleIncomingScanIntentNonBip38NoKey() {
        // Arrange
        val intent = Intent().apply {
            putExtra(
                CaptureActivity.SCAN_RESULT,
                "L1FQxC7wmmRNNe2YFPNXscPq3kaheiA4T7SnTr7vYSBW7Jw1A7PD"
            )
        }
        whenever(privateKeyFactory.getFormat(anyString())).thenReturn(PrivateKeyFactory.BASE58)
        // Act
        subject.handleIncomingScanIntent(intent)
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun handleIncomingScanIntentNonBip38WithKey() {
        // Arrange
        val legacyAddress = LegacyAddress().apply { address = "" }
        subject.legacyAddress = legacyAddress
        val intent = Intent().apply {
            putExtra(
                CaptureActivity.SCAN_RESULT,
                "L1FQxC7wmmRNNe2YFPNXscPq3kaheiA4T7SnTr7vYSBW7Jw1A7PD"
            )
        }
        whenever(privateKeyFactory.getFormat(anyString())).thenReturn(PrivateKeyFactory.BASE58)
        whenever(privateKeyFactory.getKey(anyString(), anyString())).thenReturn(ECKey())
        // Act
        subject.handleIncomingScanIntent(intent)
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()

        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun setSecondPassword() {
        // Arrange

        // Act
        subject.secondPassword = "password"
        // Assert
        assertEquals("password", subject.secondPassword)
    }

    @Test
    fun archiveAccountSuccess() {
        // Arrange
        subject.account = Account()
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        whenever(payloadDataManager.updateAllTransactions()).thenReturn(Completable.complete())
        // Act
        subject.archiveAccount()
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).setActivityResult(anyInt())
        verify(payloadDataManager).syncPayloadWithServer()
        verify(payloadDataManager).updateAllTransactions()
    }

    @Test
    fun archiveAccountFailed() {
        // Arrange
        subject.account = Account()
        whenever(payloadDataManager.syncPayloadWithServer())
            .thenReturn(Completable.error(Throwable()))
        whenever(payloadDataManager.updateAllTransactions())
            .thenReturn(Completable.complete())
        // Act
        subject.archiveAccount()
        // Assert
        verify(payloadDataManager).syncPayloadWithServer()
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun importBIP38AddressError() {
        // Arrange

        // Act
        subject.importBIP38Address("", "")
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(view).dismissProgressDialog()
    }

    @Ignore("This test is failing because of https://github.com/robolectric/robolectric/issues/3839")
    @Test
    fun importBIP38AddressValidAddressEmptyKey() {
        // Arrange

        // Act
        subject.importBIP38Address(
            "6PRJmkckxBct8jUwn6UcJbickdrnXBiPP9JkNW83g4VyFNsfEuxas39pSS",
            ""
        )
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(view).dismissProgressDialog()
    }

    @Ignore("Cannot decrypt key in test VM")
    @Test
    fun importBIP38AddressValidAddressWithKey() {
        // Arrange
        val legacyAddress = LegacyAddress()
        legacyAddress.address = ""
        subject.legacyAddress = legacyAddress
        // Act
        subject.importBIP38Address(
            "6PYX4iD7a39UeAsd7RQiwHFjgbRwJVLhfEHxcvTD4HPKxK1JSnkPZ7jben",
            "password"
        )
        // Assert
        verify(view).showProgressDialog(anyInt())
        verify(view).dismissProgressDialog()
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(view).dismissProgressDialog()
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun importAddressPrivateKeySuccessMatchesIntendedAddressNoDoubleEncryption() {
        // Arrange
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val mockEcKey: ECKey = mock()
        whenever(mockEcKey.privKeyBytes).thenReturn("privkey".toByteArray())
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.importAddressPrivateKey(mockEcKey, LegacyAddress(), true)
        // Assert
        verify(view).setActivityResult(anyInt())
        verify(accountEditModel).scanPrivateKeyVisibility = anyInt()
        verify(accountEditModel).archiveVisibility = anyInt()
        verify(view).privateKeyImportSuccess()
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun importAddressPrivateKeySuccessNoAddressMatchDoubleEncryption() {
        // Arrange
        subject.secondPassword = "password"
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(true)
        val mockOptions: Options = mock()
        whenever(mockOptions.pbkdf2Iterations).thenReturn(1)
        whenever(mockPayload.options).thenReturn(mockOptions)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val mockEcKey: ECKey = mock()
        whenever(mockEcKey.privKeyBytes).thenReturn("privkey".toByteArray())
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.importAddressPrivateKey(mockEcKey, LegacyAddress(), false)
        // Assert
        verify(view).setActivityResult(anyInt())
        verify(accountEditModel).scanPrivateKeyVisibility = anyInt()
        verify(accountEditModel).archiveVisibility = anyInt()
        verify(view).privateKeyImportMismatch()
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun importAddressPrivateKeyFailed() {
        // Arrange
        subject.secondPassword = "password"
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(true)
        val mockOptions: Options = mock()
        whenever(mockOptions.pbkdf2Iterations).thenReturn(1)
        whenever(mockPayload.options).thenReturn(mockOptions)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val mockEcKey: ECKey = mock()
        whenever(mockEcKey.privKeyBytes).thenReturn("privkey".toByteArray())
        whenever(payloadDataManager.syncPayloadWithServer())
            .thenReturn(Completable.error(Throwable()))
        // Act
        subject.importAddressPrivateKey(mockEcKey, LegacyAddress(), false)
        // Assert

        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
    }

    @Test
    fun importUnmatchedPrivateKeyFoundInPayloadSuccess() {
        // Arrange
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        val legacyStrings = Arrays.asList("addr0", "addr1", "addr2")
        whenever(mockPayload.legacyAddressStringList).thenReturn(legacyStrings)
        val legacyAddress = LegacyAddress()
        legacyAddress.address = "addr0"
        val legacyAddresses = listOf(legacyAddress)
        whenever(mockPayload.legacyAddressList).thenReturn(legacyAddresses)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val mockEcKey: ECKey = mock()
        whenever(mockEcKey.privKeyBytes).thenReturn("privkey".toByteArray())
        val mockAddress: Address = mock()
        whenever(mockAddress.toString()).thenReturn("addr0")
        whenever(mockEcKey.toAddress(any(NetworkParameters::class))).thenReturn(mockAddress)
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.importUnmatchedPrivateKey(mockEcKey)
        // Assert
        verify(view).setActivityResult(anyInt())

        verify(accountEditModel).scanPrivateKeyVisibility = anyInt()
        verify(view).privateKeyImportMismatch()
    }

    @Test
    fun importUnmatchedPrivateNotFoundInPayloadSuccess() {
        // Arrange
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(mockPayload.legacyAddressList).thenReturn(ArrayList())
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val ecKey = ECKey()
        val mockAddress: Address = mock()
        whenever(mockAddress.toString()).thenReturn("addr0")
        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.importUnmatchedPrivateKey(ecKey)
        // Assert
        verify(view).setActivityResult(anyInt())
        verify(view).sendBroadcast(anyString(), anyString())
        verify(view).privateKeyImportMismatch()
    }

    @Test
    fun importUnmatchedPrivateNotFoundInPayloadFailure() {
        // Arrange
        val mockPayload: Wallet = mock()
        whenever(mockPayload.isDoubleEncryption).thenReturn(false)
        whenever(mockPayload.legacyAddressList).thenReturn(ArrayList())
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        val ecKey = ECKey()
        val mockAddress: Address = mock()
        whenever(mockAddress.toString()).thenReturn("addr0")
        whenever(payloadDataManager.syncPayloadWithServer())
            .thenReturn(Completable.error(Throwable()))
        // Act
        subject.importUnmatchedPrivateKey(ecKey)
        // Assert
        verify(view).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(view).privateKeyImportMismatch()
    }
}