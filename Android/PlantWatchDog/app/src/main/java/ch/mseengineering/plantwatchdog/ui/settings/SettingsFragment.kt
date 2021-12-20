package ch.mseengineering.plantwatchdog.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import ch.mseengineering.plantwatchdog.MainActivity
import ch.mseengineering.plantwatchdog.databinding.FragmentSettingsBinding
import ch.mseengineering.plantwatchdog.services.Firestore
import ch.mseengineering.plantwatchdog.services.StoreData
import ch.mseengineering.plantwatchdog.ui.authentication.LoginActivity
import com.google.android.material.slider.RangeSlider
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var firestore: Firestore
    private lateinit var logoutButton: Button
    private lateinit var submitButton: Button
    private lateinit var tempSlider: RangeSlider
    private lateinit var humiSlider: RangeSlider
    private lateinit var moisSlider: RangeSlider

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        logoutButton = binding.logoutButton
        logoutButton.setOnClickListener(logoutButtonListener)


        submitButton = binding.submitRangeButton
        submitButton.setOnClickListener(submitButtonListener)

        tempSlider = binding.tempSlider
        humiSlider = binding.humiSlider
        moisSlider = binding.moisSlider

        firestore = Firestore()

        val mainActivity = activity as MainActivity
        submitButton.isEnabled = mainActivity.isConnected
        tempSlider.isEnabled = mainActivity.isConnected
        humiSlider.isEnabled = mainActivity.isConnected
        moisSlider.isEnabled = mainActivity.isConnected

        loadSettings()

        return root
    }

    // click listener of the submit button
    // saves the values to the database and triggers the send of those to the device
    private val submitButtonListener = View.OnClickListener {
        saveSliderValues()
        sendSliderValues()
    }

    // saves the slider values to the database
    private fun saveSliderValues() {
        firestore.setTemperatureSettings(tempSlider.values)
        firestore.setHumiditySettings(humiSlider.values)
        firestore.setMoistureSettings(moisSlider.values)
    }

    // sends the slider values to the connected plant watch dog device
    private fun sendSliderValues() {
        val main = activity as MainActivity
        if (main.isConnected) {
            main.addTemperatureToWriteQueue(tempSlider.values)
            main.addHumidityToWriteQueue(humiSlider.values)
            main.addMoistureToWriteQueue(moisSlider.values)
            main.startWriteQueueWorker {
                Toast.makeText(requireContext(), "Settings sent to device.", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // click listener of the logout button
    // logs out the user and disconnects the device
    // starts the login activity
    private val logoutButtonListener = View.OnClickListener {
        val storeData = StoreData()
        storeData.save("userId", null)

        FirebaseAuth.getInstance().signOut()

        val main = activity as MainActivity
        if (main.isConnected) main.disconnect()

        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // loads the current range values from the database and updates the sliders
    private fun loadSettings() {
        firestore.getTemperatureSettings {
            if (it != null && it.count() == 2) tempSlider.values = it
        }
        firestore.getHumiditySettings {
            if (it != null && it.count() == 2) humiSlider.values = it
        }
        firestore.getMoistureSettings {
            if (it != null && it.count() == 2) moisSlider.values = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}