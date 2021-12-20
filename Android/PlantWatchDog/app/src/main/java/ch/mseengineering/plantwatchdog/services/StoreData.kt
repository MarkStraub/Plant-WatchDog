package ch.mseengineering.plantwatchdog.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.firebase.ui.auth.AuthUI.getApplicationContext

/*
    Code based on
    https://developer.android.com/training/data-storage/shared-preferences
    licensed under MIT Apache 2.0

    and
    https://stackoverflow.com/questions/11316560/sharedpreferences-from-different-activity
    https://stackoverflow.com/questions/56833657/preferencemanager-getdefaultsharedpreferences-deprecated-in-android-q/56911496
 */

class StoreData() {
    @SuppressLint("RestrictedApi")
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

    fun save(key: String, value: Number) {
        if (sharedPref !== null) {
            with(sharedPref.edit()) {
                putInt(key, value.toInt());
                apply();
            }
        } else {
            Log.d("storedata", "Shared Preference is null");
        }
    }

    fun save(key: String, value: String?) {
        if (sharedPref !== null) {
            with(sharedPref.edit()) {
                putString(key, value)
                apply();
            }
        } else {
            Log.d("storedata", "Shared Preference is null");
        }
    }

    fun readString(key: String): String? {
        return if (sharedPref !== null) sharedPref.getString(key, null) else null;
    }

    fun readInt(key: String): Int? {
        if (sharedPref !== null) {
            return sharedPref.getInt(key, 0);
        } else {
            this.save(key, 0);
        }

        return null;
    }
}