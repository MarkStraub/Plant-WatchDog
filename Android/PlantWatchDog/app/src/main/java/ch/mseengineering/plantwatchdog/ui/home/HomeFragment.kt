package ch.mseengineering.plantwatchdog.ui.home

import android.bluetooth.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.ListFragment
import ch.mseengineering.plantwatchdog.databinding.FragmentHomeBinding
import kotlin.collections.ArrayList
import ch.mseengineering.plantwatchdog.MainActivity
import ch.mseengineering.plantwatchdog.R


class HomeFragment : ListFragment() {

    private lateinit var connectionStateText: TextView
    private lateinit var bleButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var watchDogListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val devices: MutableList<BluetoothDevice> = ArrayList()
    private val deviceNames: MutableList<String> = ArrayList()
    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        bleButton = binding.bleButton
        bleButton.setOnClickListener(bleButtonListener)

        disconnectButton = binding.disconnectButton
        disconnectButton.setOnClickListener(disconnectButtonListener)

        watchDogListView = binding.list
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceNames)
        watchDogListView.adapter = adapter

        connectionStateText = binding.connectionTextView
        updateUiOnUiThread()

        return root
    }

    // click listener of an item in the device list
    // starts the connection process and when the connection was established, updates the ui
    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        (activity as MainActivity).connect(
            devices[position],
            object : MainActivity.WatchDogConnectionCallback {
                override fun onConnectionChanged() {
                    updateUiOnUiThread()
                }
            })
    }

    // click listener of the scan button
    // while scanning, the button is grayed out and the text is changed
    private val bleButtonListener = View.OnClickListener {
        bleButton.isEnabled = false;
        bleButton.text = resources.getString(R.string.connect_button_scanning_text)
        devices.clear()
        deviceNames.clear()
        adapter.notifyDataSetChanged()

        (activity as MainActivity).getWatchDogDeviceList(object :
            MainActivity.WatchDogScanCallback {
            override fun onSuccess(bleDevices: List<BluetoothDevice>) {
                devices.addAll(bleDevices)
                devices.forEach { d -> deviceNames.add("${d.address} - ${d.name}") }
                adapter.notifyDataSetChanged()
                bleButton.text = resources.getString(R.string.connect_button_text)
                bleButton.isEnabled = true
            }

            override fun onReject() {
                bleButton.text = resources.getString(R.string.connect_button_text)
                bleButton.isEnabled = true
            }
        })
    }

    // click listener of the disconnect button
    // when the button is pressed, the disconnect process is started and the ui is updated
    private val disconnectButtonListener = View.OnClickListener {
        (activity as MainActivity).disconnect()
        updateUiOnUiThread()
    }

    // updates the enable state of the buttons and the text of the connection state text field
    // ensure execution on ui thread: https://stackoverflow.com/questions/16425146/runonuithread-in-fragments
    private fun updateUiOnUiThread() {
        requireActivity().runOnUiThread(object : Runnable {
            override fun run() {
                val isConnected = (activity as MainActivity).isConnected
                bleButton.isEnabled = !isConnected
                disconnectButton.isEnabled = isConnected
                if (isConnected) {
                    connectionStateText.text =
                        resources.getString(R.string.connection_text_connected)
                } else {
                    connectionStateText.text =
                        resources.getString(R.string.connection_text_not_connected)
                }
            }
        })
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}