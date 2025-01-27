package piuk.blockchain.android.ui.contacts.payments

import android.support.annotation.VisibleForTesting
import com.blockchain.annotations.BurnCandidate
import info.blockchain.wallet.contacts.data.Contact
import info.blockchain.wallet.contacts.data.PaymentCurrency
import info.blockchain.wallet.contacts.data.PaymentRequest
import info.blockchain.wallet.contacts.data.RequestForPaymentRequest
import io.reactivex.Completable
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.account.PaymentConfirmationDetails
import piuk.blockchain.android.ui.contacts.payments.ContactConfirmRequestFragment.Companion.ARGUMENT_ACCOUNT_POSITION
import piuk.blockchain.android.ui.contacts.payments.ContactConfirmRequestFragment.Companion.ARGUMENT_CONFIRMATION_DETAILS
import piuk.blockchain.android.ui.contacts.payments.ContactConfirmRequestFragment.Companion.ARGUMENT_CONTACT_ID
import piuk.blockchain.android.ui.contacts.payments.ContactConfirmRequestFragment.Companion.ARGUMENT_REQUEST_TYPE
import piuk.blockchain.android.ui.contacts.payments.ContactConfirmRequestFragment.Companion.ARGUMENT_SATOSHIS
import piuk.blockchain.android.util.extensions.addToCompositeDisposable
import piuk.blockchain.androidcore.data.contacts.ContactsDataManager
import piuk.blockchain.androidcore.data.contacts.ContactsPredicates
import piuk.blockchain.androidcore.data.contacts.models.PaymentRequestType
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.utils.logging.ContactEventType
import piuk.blockchain.androidcoreui.utils.logging.ContactsEvent
import piuk.blockchain.androidcoreui.utils.logging.Logging
import javax.inject.Inject

@BurnCandidate("Contacts are not used")
class ContactConfirmRequestPresenter @Inject internal constructor(
    private val contactsDataManager: ContactsDataManager,
    private val payloadDataManager: PayloadDataManager
) : BasePresenter<ContactConfirmRequestView>() {

    @VisibleForTesting
    internal var recipient: Contact? = null
    @VisibleForTesting
    internal var satoshis: Long = 0
    @VisibleForTesting
    internal var confirmationDetails: PaymentConfirmationDetails? = null
    @VisibleForTesting
    internal var paymentRequestType: PaymentRequestType? = null
    @VisibleForTesting
    internal var accountPosition: Int = -1

    override fun onViewReady() {
        val fragmentBundle =
            view.fragmentBundle ?: throw IllegalArgumentException("Fragment bundle is null")
        val contactId = fragmentBundle.getString(ARGUMENT_CONTACT_ID)
        accountPosition = fragmentBundle.getInt(ARGUMENT_ACCOUNT_POSITION, -1)
        paymentRequestType = fragmentBundle.getSerializable(ARGUMENT_REQUEST_TYPE) as PaymentRequestType
        confirmationDetails = fragmentBundle.getParcelable(ARGUMENT_CONFIRMATION_DETAILS)
        satoshis = fragmentBundle.getLong(ARGUMENT_SATOSHIS)

        if (contactId != null && confirmationDetails != null && paymentRequestType != null) {
            updateUi(confirmationDetails!!, paymentRequestType!!)
            loadContact(contactId)
        } else {
            throw IllegalArgumentException(
                "Contact ID, confirmation details, payment request type and satoshi amount must be passed to fragment"
            )
        }
    }

    internal fun sendRequest() {
        view.showProgressDialog()

        val completable: Completable

        if (paymentRequestType == PaymentRequestType.SEND) {
            val request = RequestForPaymentRequest(satoshis, view.note, PaymentCurrency.BITCOIN)
            // Request that the other person receives payment, ie you send
            completable = contactsDataManager.requestReceivePayment(recipient!!.mdid, request)
                .doAfterTerminate { view.dismissProgressDialog() }
                .doOnComplete {
                    Logging.logCustom(
                        ContactsEvent(ContactEventType.RPR)
                    )
                }
                .addToCompositeDisposable(this)
        } else {
            val paymentRequest = PaymentRequest(satoshis, view.note, PaymentCurrency.BITCOIN)
            // Request that the other person sends payment, ie you receive
            completable = payloadDataManager.getNextReceiveAddress(accountPosition)
                .doOnNext { address -> paymentRequest.address = address }
                .flatMapCompletable {
                    contactsDataManager.requestSendPayment(
                        recipient!!.mdid,
                        paymentRequest
                    )
                }
                .doAfterTerminate({ view.dismissProgressDialog() })
                .doOnComplete {
                    Logging.logCustom(
                        ContactsEvent(ContactEventType.PR)
                    )
                }
                .addToCompositeDisposable(this)
        }

        completable.subscribe(
            {
                view.onRequestSuccessful(
                    paymentRequestType ?: throw IllegalStateException("Request type is null"),
                    recipient!!.name!!,
                    "${confirmationDetails!!.cryptoAmount} ${confirmationDetails!!.cryptoUnit}"
                )
            },
            {
                view.showToast(
                    R.string.contacts_error_sending_payment_request,
                    ToastCustom.TYPE_ERROR
                )
            })
    }

    private fun updateUi(
        confirmationDetails: PaymentConfirmationDetails,
        paymentRequestType: PaymentRequestType
    ) {
        view.updateAccountName(confirmationDetails.fromLabel)
        view.updateTotalBtc("${confirmationDetails.cryptoAmount} ${confirmationDetails.cryptoUnit}")
        view.updateTotalFiat("${confirmationDetails.fiatSymbol}${confirmationDetails.fiatAmount}")
        view.updatePaymentType(paymentRequestType)
    }

    private fun loadContact(contactId: String) {
        contactsDataManager.getContactList()
            .addToCompositeDisposable(this)
            .filter(ContactsPredicates.filterById(contactId))
            .subscribe(
                { contact ->
                    recipient = contact
                    view.contactLoaded(recipient!!.name!!)
                },
                {
                    view.showToast(R.string.contacts_not_found_error, ToastCustom.TYPE_ERROR)
                    view.finishPage()
                },
                {
                    if (recipient == null) {
                        // Wasn't found via filter, show not found
                        view.showToast(R.string.contacts_not_found_error, ToastCustom.TYPE_ERROR)
                        view.finishPage()
                    }
                })
    }
}
