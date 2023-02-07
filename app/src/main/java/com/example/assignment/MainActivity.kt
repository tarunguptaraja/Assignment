package com.example.assignment

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

private val REQUEST_CODE = 1
private val CAMERA_REQUEST_CODE =0
private lateinit var imei: String
private lateinit var fusedLocationClient: FusedLocationProviderClient
lateinit var mHandler: Handler
val map = HashMap<String, Any>()
val db = Firebase.firestore



class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                REQUEST_CODE
            )
        }

        try{
            load()
        }catch(e:Exception){
            val imeiTextView = findViewById<TextView>(R.id.imei)
            val internet = findViewById<TextView>(R.id.internet)
            val charging = findViewById<TextView>(R.id.charging)
            val percent = findViewById<TextView>(R.id.percent)
            val locationTxtView = findViewById<TextView>(R.id.location)
            val imei: String = try{
                getIMEI()
            }catch (e:Exception){
                "imei"
            }
            db.collection("data").document(imei).get().addOnSuccessListener { document ->
                imeiTextView.text= document.get("imei") as String
                internet.text= document.get("internet_status") as String
                charging.text= document.get("charging_status") as String
                percent.text= document.get("battery_pct") as String
                locationTxtView.text = document.get("location") as String
            }
        }
        updateData()
        mHandler = Handler(Looper.getMainLooper())

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            load()
            updateData()
        }

        val cameraButton = findViewById<ImageButton>(R.id.cameraButton)
        cameraButton.setOnClickListener {
            openCamera()
        }

    }

    override fun onResume() {
        super.onResume()

        mHandler.post(updateTextTask)
    }
    private val updateTextTask = object : Runnable {
        override fun run() {
            load()
            updateData()
            mHandler.postDelayed(this, 900000L)
        }
    }

    private fun updateData() {

        if(map.containsKey("imei")) db.collection("data").document(map["imei"] as String).set(map)
        else db.collection("data").document("imei").set(map)
    }



    private fun load(){

        val imeiTextView = findViewById<TextView>(R.id.imei)
        val internet = findViewById<TextView>(R.id.internet)
        val charging = findViewById<TextView>(R.id.charging)
        val percent = findViewById<TextView>(R.id.percent)
        val locationTxtView = findViewById<TextView>(R.id.location)
        val timestamp = findViewById<TextView>(R.id.timestamp)


        try {
            val imei=getIMEI()
            imeiTextView.text = imei
            map["imei"] = imei
        } catch (e: java.lang.Exception) {
            imeiTextView.text = e.toString()
        }


        if (checkInternet(this)) {
            internet.text = "Connected"
            internet.setTextColor(Color.GREEN)
            map["internet_status"] = "Connected"
        } else {
            internet.text = "Disconnected"
            internet.setTextColor(Color.RED)
            map["internet_status"] = "Disconnected"
        }

        val batteryStatus: Intent? = registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        if (isCharging) {
            charging.text = "Connected"
            map["charging_status"] = "Connected"
            charging.setTextColor(Color.GREEN)
        } else {
            charging.text = "Disconnected"
            map["charging_status"] = "Disconnected"
        }

        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        percent.text = batteryPct.toString()
        map["battery_pct"] = batteryPct.toString()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_CODE
            )
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location : Location? ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val cityName = addresses?.get(0)?.locality
                if (cityName != null) {
                    if (cityName.isNotEmpty())
                        locationTxtView.text = cityName
                }else
                    locationTxtView.text = "${location.latitude} ${location.longitude}"
                map["location"] = "${location.latitude},${location.longitude}"
            }
        }



        val current = LocalDateTime.now()
        timestamp.text="${current.toLocalDate()} ${current.toLocalTime()}"
        map["timestamp"] = current

    }

    private fun getIMEI(): String {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.imei
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            // Do something with the image, for example, save it or display it in an ImageView
            saveImage(imageBitmap)
        }
    }

    private fun saveImage(imageBitmap: Bitmap) {
        val filename = "image_" + System.currentTimeMillis() + ".jpg"
        val imagePath = File(
            Environment.getExternalStorageDirectory().toString() + "/assignment/",
            filename
        )
        try {
            val stream: OutputStream = FileOutputStream(imagePath)
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkInternet(context: Context): Boolean{

        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val network = connectivityManager.activeNetwork ?:return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                else -> false
            }
        }else{
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}




