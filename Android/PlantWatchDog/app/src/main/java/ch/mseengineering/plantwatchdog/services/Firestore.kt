package ch.mseengineering.plantwatchdog.services

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.collections.HashMap

/*
    Code based on
    https://firebase.google.com/docs/firestore/manage-data/add-data?utm_source=studio#kotlin+ktx
    https://firebase.google.com/docs/reference/kotlin/com/google/firebase/Timestamp
    https://firebase.google.com/docs/firestore/query-data/get-data?utm_source=studio#kotlin+ktx_5
    https://firebase.google.com/docs/firestore/query-data/listen
    licensed under MIT Apache 2.0

    and
    https://stackoverflow.com/questions/51594772/how-to-return-a-list-from-firestore-database-as-a-result-of-a-function-in-kotlin/59124705#59124705
 */

class Firestore {
    private var TAG: String = "firebase";
    private var db: FirebaseFirestore = Firebase.firestore;
    private var storeData: StoreData = StoreData();

    private var userId: String = storeData.readString("userId") ?: "";

    init {
        this.getIncrementation();
    }

    fun setHumidityData(humidity: Number) {
        val data = hashMapOf(
            "humidity" to humidity,
            "created" to Timestamp.now()
        );
        this.setData("humidity", data);
    }

    fun setMoistureData(moisture: Number) {
        val data = hashMapOf(
            "moisture" to moisture,
            "created" to Timestamp.now()
        );
        this.setData("moisture", data);
    }

    fun setTemperatureData(temperature: Number) {
        val data = hashMapOf(
            "temperature" to temperature,
            "created" to Timestamp.now()
        );
        this.setData("temperature", data);
    }

    /**
     * Write the data to Firestore Database
     */
    private fun setData(collection: String, data: HashMap<String, Any>) {
        var inc = this.storeData.readInt(collection + "Inc") ?: 0;
        db.collection("data")
            .document(userId)
            .collection(collection)
            .document(inc.toString().padStart(20, '0'))
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "$collection added");
            }
            .addOnFailureListener { e -> Log.d(TAG, "Error adding $collection", e); }
        this.setIncrementation(collection, ++inc);
    }

    fun getLatestHumidityData(myCallback: (List<Number?>) -> Unit) {
        this.getLatestData("humidity", myCallback);
    }

    fun getLatestMoistureData(myCallback: (List<Number?>) -> Unit) {
        this.getLatestData("moisture", myCallback);
    }

    fun getLatestTemperatureData(myCallback: (List<Number?>) -> Unit) {
        this.getLatestData("temperature", myCallback);
    }

    /**
     * Read the latest data from Firestore Database
     */
    private fun getLatestData(dataType: String, myCallback: (List<Number?>) -> Unit) {
        val colRef = db.collection("data").document(userId).collection(dataType);
        colRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return@addSnapshotListener;
            }
            val list = ArrayList<Number?>();
            if (snapshot != null && !snapshot.isEmpty) {
                val data = snapshot.documents[snapshot.documents.size - 1].data;
                val value =
                    if (data != null && data.containsKey(dataType)) data.getValue(dataType) as Number else null;
                list.add(value);
            } else {
                Log.d(TAG, "Current data: null");
                list.add(null);
            }

            myCallback(list);
        }
    }

    fun getHumidityData(myCallback: (List<Number?>) -> Unit) {
        this.getData("humidity", myCallback);
    }

    fun getMoistureData(myCallback: (List<Number?>) -> Unit) {
        this.getData("moisture", myCallback);
    }

    fun getTemperatureData(myCallback: (List<Number?>) -> Unit) {
        this.getData("temperature", myCallback);
    }

    /**
     * Read the data from Firestore Database
     */
    private fun getData(dataType: String, myCallback: (List<Number?>) -> Unit) {
        val colRef = db.collection("data").document(userId).collection(dataType);
        colRef.get()
            .addOnSuccessListener { snapshot ->
                val list = ArrayList<Number?>();
                if (snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.forEach {
                        var data = it.data;
                        val value =
                            if (data != null && data.containsKey(dataType)) data.getValue(dataType) as Number else null;
                        list.add(value);
                    }

                } else {
                    Log.d(TAG, "Current data: null");
                    list.add(null);
                }

                myCallback(list);
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception);
            }
    }

    /**
     * Return the incrementation Data
     */
    private fun getIncrementation() {
        val docRef = db.collection("incrementation").document(userId);
        docRef.get()
            .addOnSuccessListener { document ->
                var humidityInc = (0).toLong();
                var moistureInc = (0).toLong();
                var temperatureInc = (0).toLong();

                if (document?.data != null) {
                    humidityInc = document.data?.getValue("humidity") as Long;
                    moistureInc = document.data?.getValue("moisture") as Long;
                    temperatureInc = document.data?.getValue("temperature") as Long;
                    Log.d(TAG, "Current Incrementation data : ${document.data}");
                } else {
                    Log.d(TAG, "Create new Incrementation data");

                    db.collection("incrementation")
                        .document(userId)
                        .set(hashMapOf("humidity" to 0, "moisture" to 0, "temperature" to 0))
                        .addOnSuccessListener { Log.d(TAG, "Incrementation data added"); }
                        .addOnFailureListener { e ->
                            Log.d(TAG, "Error adding Incrementation data", e);
                        }
                }

                // Save data to shared preference
                this.storeData.save("humidityInc", humidityInc);
                this.storeData.save("moistureInc", moistureInc);
                this.storeData.save("temperatureInc", temperatureInc);

            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception);
            };
    }

    /**
     * Update the incrementation Data
     */
    private fun setIncrementation(collection: String, value: Number) {
        this.storeData.save(collection + "Inc", value);

        val incrementationRef = db.collection("incrementation").document(userId);

        incrementationRef
            .update(collection, value)
            .addOnSuccessListener { Log.d(TAG, "$collection successfully updated!") }
            .addOnFailureListener { e -> Log.w(TAG, "Error updating $value", e) };
    }


    fun setImagePath(path: String) {
        val data = hashMapOf(
            "imagePath" to path,
            "created" to Timestamp.now()
        );
        db.collection("path")
            .document(userId)
            .set(data)
            .addOnSuccessListener { Log.d("firebase", "DocumentSnapshot added with ID: "); }
            .addOnFailureListener { e -> Log.e("firebase", "Error adding document", e); };
    }

    fun getImagePath(myCallback: (List<String?>) -> Unit) {
        val docRef = db.collection("path").document(userId)

        docRef.get()
            .addOnSuccessListener { document ->
                val list = ArrayList<String?>();
                val path =
                    if (document?.data != null && document.data!!.containsKey("imagePath")) document.data!!.getValue(
                        "imagePath"
                    ) as String else null;
                list.add(path);
                myCallback(list);
            }
    }

    fun setTemperatureSettings(values: List<Float>) {
        setSettingData(values, "temperature")
    }

    fun setHumiditySettings(values: List<Float>) {
        setSettingData(values, "humidity")
    }

    fun setMoistureSettings(values: List<Float>) {
        setSettingData(values, "moisture")
    }

    private fun setSettingData(values: List<Float>, collection: String) {
        val data = hashMapOf(
            "min" to values[0],
            "max" to values[1],
            "created" to Timestamp.now()
        )

        db.collection("settings")
            .document(userId)
            .collection(collection)
            .document("latest")
            .set(data)
            .addOnSuccessListener { Log.d("firebase", "DocumentSnapshot added with ID: "); }
            .addOnFailureListener { e -> Log.e("firebase", "Error adding document", e); };
    }

    fun getTemperatureSettings(myCallback: (List<Float>?) -> Unit) {
        getSettingData("temperature", myCallback)
    }

    fun getHumiditySettings(myCallback: (List<Float>?) -> Unit) {
        getSettingData("humidity", myCallback)
    }

    fun getMoistureSettings(myCallback: (List<Float>?) -> Unit) {
        getSettingData("moisture", myCallback)
    }

    private fun getSettingData(collection: String, myCallback: (List<Float>?) -> Unit) {
        val docRef = db
            .collection("settings")
            .document(userId)
            .collection(collection)
            .document("latest")

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.contains("min") && document.contains("max")) {
                    var min = (document.get("min") as Number).toFloat()
                    var max = (document.get("max") as Number).toFloat()
                    myCallback(listOf(min, max))
                }
                myCallback(null)
            }

    }
}