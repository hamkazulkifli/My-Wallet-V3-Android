package piuk.blockchain.androidbuysellui.ui.signup

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.RxTest
import piuk.blockchain.android.ui.buysell.coinify.signup.select_country.CoinifySelectCountryPresenter
import piuk.blockchain.android.ui.buysell.coinify.signup.select_country.CoinifySelectCountryView

class CoinifySelectCountryPresenterTest: RxTest() {

    private lateinit var subject: CoinifySelectCountryPresenter

    private val view: CoinifySelectCountryView = mock()

    @Before
    fun setup() {
        subject = CoinifySelectCountryPresenter()
        subject.initView(view)
    }

    @Test
    fun `onViewReady`() {

        // Arrange

        // Act
        subject.onViewReady()

        // Assert
        verify(view).onSetCountryPickerData(any())
        verify(view).onAutoSelectCountry(any())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `collectDataAndContinue 0`() {

        // Arrange
        subject.onViewReady()

        // Act
        subject.collectDataAndContinue(0)

        // Assert
        verify(view).onSetCountryPickerData(any())
        verify(view).onAutoSelectCountry(any())
        verify(view).onStartVerifyEmail("AF")
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `collectDataAndContinue 5`() {

        // Arrange
        subject.onViewReady()

        // Act
        subject.collectDataAndContinue(5)

        // Assert
        verify(view).onSetCountryPickerData(any())
        verify(view).onAutoSelectCountry(any())
        verify(view).onStartVerifyEmail("AO")
        verifyNoMoreInteractions(view)
    }
}