package com.phelat.tedu.backup.viewmodel

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phelat.tedu.analytics.ExceptionLogger
import com.phelat.tedu.analytics.di.qualifier.Development
import com.phelat.tedu.backup.di.scope.BackupScope
import com.phelat.tedu.backup.entity.WebDavCredentials
import com.phelat.tedu.backup.error.BackupErrorContext
import com.phelat.tedu.backup.state.WebDavViewState
import com.phelat.tedu.backup.usecase.BackupUseCase
import com.phelat.tedu.coroutines.Dispatcher
import com.phelat.tedu.datasource.Readable
import com.phelat.tedu.datasource.Writable
import com.phelat.tedu.functional.Response
import com.phelat.tedu.functional.ifSuccessful
import com.phelat.tedu.functional.otherwise
import com.phelat.tedu.functional.unBox
import com.phelat.tedu.lifecycle.SingleLiveData
import com.phelat.tedu.lifecycle.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@BackupScope
class WebDavViewModel @Inject constructor(
    credentialsReadable: Readable<Response<WebDavCredentials, BackupErrorContext>>,
    private val credentialsWritable: Writable<WebDavCredentials>,
    @Development private val logger: ExceptionLogger,
    private val webDavBackupUseCase: BackupUseCase,
    private val dispatcher: Dispatcher
) : ViewModel() {

    private val _viewStateObservable = MutableLiveData(WebDavViewState())
    val viewStateObservable: LiveData<WebDavViewState> = _viewStateObservable

    private val _credentialsObservable = SingleLiveData<WebDavCredentials>()
    val credentialsObservable: LiveData<WebDavCredentials> = _credentialsObservable

    init {
        credentialsReadable.read()
            .ifSuccessful(_credentialsObservable::setValue)
            .otherwise(logger::log)
    }

    fun onUrlTextChange(url: String) {
        _viewStateObservable.update {
            copy(
                isSaveButtonEnabled = if (
                    url.isEmpty() ||
                    url.matches(Patterns.WEB_URL.toRegex()).not()
                ) {
                    isSaveButtonEnabled.switchOff(URL_FIELD_SWITCH)
                } else {
                    isSaveButtonEnabled.switchOn(URL_FIELD_SWITCH)
                }
            )
        }
    }

    fun onUsernameTextChange(username: String) {
        _viewStateObservable.update {
            copy(
                isSaveButtonEnabled = if (username.isEmpty()) {
                    isSaveButtonEnabled.switchOff(USERNAME_FIELD_SWITCH)
                } else {
                    isSaveButtonEnabled.switchOn(USERNAME_FIELD_SWITCH)
                }
            )
        }
    }

    fun onPasswordTextChange(password: String) {
        _viewStateObservable.update {
            copy(
                isSaveButtonEnabled = if (password.isEmpty()) {
                    isSaveButtonEnabled.switchOff(PASSWORD_FIELD_SWITCH)
                } else {
                    isSaveButtonEnabled.switchOn(PASSWORD_FIELD_SWITCH)
                }
            )
        }
    }

    fun onSaveCredentialsClick(url: String, username: String, password: String) {
        viewModelScope.launch {
            _viewStateObservable.update {
                copy(isSaveProgressVisible = true, isSaveButtonVisible = false)
            }
            val credentials = WebDavCredentials(url, username, password)
            credentialsWritable.write(credentials)
            withContext(dispatcher.iO) { webDavBackupUseCase.sync() }
                .unBox {
                    _viewStateObservable.update {
                        copy(isSaveProgressVisible = false, isSaveButtonVisible = true)
                    }
                    failure(logger::log)
                }
        }
    }

    companion object {
        const val URL_FIELD_SWITCH = "url_field_switch"
        const val USERNAME_FIELD_SWITCH = "username_field_switch"
        const val PASSWORD_FIELD_SWITCH = "password_field_switch"
    }
}