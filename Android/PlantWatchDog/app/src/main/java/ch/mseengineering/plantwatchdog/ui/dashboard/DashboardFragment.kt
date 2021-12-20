package ch.mseengineering.plantwatchdog.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import ch.mseengineering.plantwatchdog.R
import ch.mseengineering.plantwatchdog.databinding.FragmentDashboardBinding
import ch.mseengineering.plantwatchdog.services.Firestore
import java.io.File
import java.io.FileOutputStream

/*
Get picture from camera based on
    https://developer.android.com/training/camera/photobasics
    licensed under MIT Apache 2.0
 */

class DashboardFragment : Fragment() {

    private lateinit var firestore: Firestore
    private lateinit var plantImageView: ImageView
    private lateinit var cameraButton: Button
    private lateinit var galleryButton: Button
    private lateinit var temperatureTextView: TextView
    private lateinit var humidityTextView: TextView
    private lateinit var moistureTextView: TextView
    private var imagePath: String? = null

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        plantImageView = binding.plantImageView

        cameraButton = binding.cameraButton
        cameraButton.setOnClickListener(cameraButtonListener)

        galleryButton = binding.galleryButton
        galleryButton.setOnClickListener(galleryButtonListener)

        temperatureTextView = binding.temperatureText
        humidityTextView = binding.humidityText
        moistureTextView = binding.moistureText

        firestore = Firestore()
        initializePlantImageView()
        attachMeasurementListeners()

        return root
    }

    // click listener of the camera button
    private val cameraButtonListener = View.OnClickListener { getImageFromCamera() }

    // click listener of the gallery button
    private val galleryButtonListener = View.OnClickListener { getImageFromGallery() }

    // initialize the plant image view with the string stored in the Firestore database
    private fun initializePlantImageView() {
        firestore.getImagePath { paths ->
            imagePath = paths[0]
            setPlantImageView()
        }
    }

    // attach the measurement value change listener
    // whenever temperature, humidity or moisture values are changed, the text fields are updated
    private fun attachMeasurementListeners() {
        firestore.getLatestTemperatureData { tempValues ->
            setLatestMeasurementText(tempValues[0], temperatureTextView, "°C")
        }
        firestore.getLatestHumidityData { humiValues ->
            setLatestMeasurementText(humiValues[0], humidityTextView, "%")
        }
        firestore.getLatestMoistureData { moisValues ->
            setLatestMeasurementText(moisValues[0], moistureTextView, "%")
        }
    }

    // helper function to set the text in the text view
    private fun setLatestMeasurementText(value: Number?, textView: TextView, suffix: String) {
        textView.text =
            if (value == null) resources.getString(R.string.no_data_text) else "${String.format("%.1f", value).toDouble()} $suffix"
    }

    // starts the camera and gets the shot image
    private fun getImageFromCamera() {
        val imageFile = createImageFile()
        val uri = getUriFromFile(imageFile)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        // if the intent can be resolved, launch the camera
        if (cameraIntent.resolveActivity(requireContext().packageManager) != null) {
            getCameraAction.launch(cameraIntent)
        }
    }

    // creates a new image file where the image can be stored to
    private fun createImageFile(): File {
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("Plant_", ".jpg", storageDir)
        imagePath = file.absolutePath
        return file
    }

    // helper function to get the uri of a given file
    private fun getUriFromFile(file: File): Uri {
        return FileProvider.getUriForFile(
            requireContext(),
            "ch.mseengineering.android.fileprovider",
            file
        )
    }

    // handle the camera intent
    private val getCameraAction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // if the result is successful, update the plant image view
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                setPlantImageView()
            } else {
                Toast.makeText(context, R.string.plant_camera_error, Toast.LENGTH_LONG).show()
            }
        }

    // starts the image gallery and gets the selected image
    // based on: https://stackoverflow.com/questions/5309190/android-pick-images-from-gallery
    private fun getImageFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (galleryIntent.resolveActivity(requireContext().packageManager) != null) {
            getGalleryAction.launch(galleryIntent)
        }
    }

    private val getGalleryAction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // if the intent result is successful, copy the image to a local file
            // and then update the plant image view
            if (it.resultCode == AppCompatActivity.RESULT_OK && it.data != null) {
                val uri = it.data?.data
                copyToLocalFile(uri)
                setPlantImageView()
            } else {
                Toast.makeText(context, R.string.plant_gallery_error, Toast.LENGTH_LONG).show()
            }
        }

    // helper function to copy the selected image from the gallery to a local file
    // this ensures that the image can still be displayed in the view even if deleted in the gallery
    private fun copyToLocalFile(uri: Uri?) {
        if (uri != null) {
            val imageFile = createImageFile()
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(imageFile)
            outputStream.write(inputStream?.readBytes())
            inputStream?.close()
            outputStream.close()
        } else {
            Toast.makeText(context, R.string.uri_empty_error, Toast.LENGTH_LONG).show()
        }
    }

    // updates the plant image view with the image under the new uri
    private fun setPlantImageView() {
        if (imagePath != null && imagePath != "") {
            val f = File(imagePath!!)
            plantImageView.setImageURI(Uri.fromFile(f))
        } else {
            plantImageView.setImageResource(R.drawable.happy_plant)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // save the image path to the database on view destroy
        if (imagePath != null) {
            firestore.setImagePath(imagePath!!)
        }
    }
}