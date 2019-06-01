package com.example.translait

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential

import com.google.cloud.translate.Translate //USE this package
//import com.google.api.services.translate.Translate NOT THIS ONE
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.translate.TranslateOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    private var translate: Translate? = null
    private var mCameraSource by Delegates.notNull<CameraSource>()
    private var textRecognizer by Delegates.notNull<TextRecognizer>()
    private val PERMISSION_REQUEST_CAMERA = 100
    private var myClipboard: ClipboardManager? = null
    private var myClip: ClipData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        myClipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

        btnCopy.setOnClickListener{
            copyText()
        }

        btnClear.setOnClickListener{
            tvTranslateTextAutofit!!.text = ""
        }

        btnTranslate.setOnClickListener{
            if (checkInternetConnection()) {
                getTranslateService()
                translate()
            } else {
                tvTranslateTextAutofit!!.text = getString(R.string.no_conn)
            }
        }
        startCameraSource()

    }

    fun copyText() {
        myClip = ClipData.newPlainText("text", tvTranslateTextAutofit.text);
        myClipboard?.setPrimaryClip(myClip);
        Toast.makeText(this, "Copy successful", Toast.LENGTH_SHORT).show()
    }

    private fun getTranslateService() {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        try{
            resources.openRawResource(R.raw.credentials).use { `is` ->
                val myCredentials = GoogleCredentials.fromStream(`is`)
                val translateOptions = TranslateOptions.newBuilder().setCredentials(myCredentials).build()
                translate = translateOptions.service
            }
        } catch (ioe: IOException){
            ioe.printStackTrace()

        }

    }

    private fun translate(){
        val originalText: String = tvCamResult.text.toString()
        val translation = translate!!.translate(originalText, Translate.TranslateOption.targetLanguage("en"), Translate.TranslateOption.model("base"))

        tvTranslateTextAutofit!!.text = translation.translatedText
    }

    private fun checkInternetConnection(): Boolean {

        //Check internet connection:
        val connectivityManager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo

        //Means that we are connected to a network (mobile or wi-fi)
        return activeNetwork?.isConnected == true

    }

    private fun startCameraSource() {

        //  Create text Recognizer
        textRecognizer = TextRecognizer.Builder(this).build()

        if (!textRecognizer.isOperational) {
            Toast.makeText(applicationContext, "Dependencies are not loaded yet...please try after few moment!!", Toast.LENGTH_LONG).show()
            Log.d("TAG","Dependencies are downloading....try after few moment")
            return
        }

        //  Init camera source to use high resolution and auto focus
        mCameraSource = CameraSource.Builder(applicationContext, textRecognizer)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedPreviewSize(1280, 1024)
            .setAutoFocusEnabled(true)
            .setRequestedFps(2.0f)
            .build()

        surface_camera_preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {

            }

            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                mCameraSource.stop()
            }

            @SuppressLint("MissingPermission")
            override fun surfaceCreated(p0: SurfaceHolder?) {
                try {
                    if (isCameraPermissionGranted()) {
                        mCameraSource.start(surface_camera_preview.holder)
                    } else {
                        requestForPermission()
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "Error:  ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })

        textRecognizer.setProcessor(object : Detector.Processor<TextBlock> {
            override fun release() {}

            override fun receiveDetections(detections: Detector.Detections<TextBlock>) {
                val items = detections.detectedItems

                if (items.size() <= 0) {
                    return
                }

                tvCamResult.post {
                    val stringBuilder = StringBuilder()
                    for (i in 0 until items.size()) {
                        val item = items.valueAt(i)
                        stringBuilder.append(item.value)
                        stringBuilder.append("\n")
                    }
                    tvCamResult.text = stringBuilder.toString()
                }
            }
        })
    }

    fun isCameraPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestForPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CAMERA)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != PERMISSION_REQUEST_CAMERA) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (isCameraPermissionGranted()) {
                mCameraSource.start(surface_camera_preview.holder)
            } else {
                Toast.makeText(applicationContext, "Permission need to grant", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

}


