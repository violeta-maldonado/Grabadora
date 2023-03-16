package com.jrtec.grabadora

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jrtec.grabadora.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    var recorder: MediaRecorder? = null
    var player: MediaPlayer? = null
    var archivo: File? = null

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.btnStop.visibility = View.INVISIBLE
        //BLUETOOTH
        val am = getSystemService(AUDIO_SERVICE) as AudioManager

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                Log.d( "BLUETOOTH HOME","Audio SCO state: $state")
                if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                    /*
                 * Now the connection has been established to the bluetooth device.
                 * Record audio or whatever (on another thread).With AudioRecord you can record with an object created like this:
                 * new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                 * AudioFormat.ENCODING_PCM_16BIT, audioBufferSize);
                 *
                 * After finishing, don't forget to unregister this receiver and
                 * to stop the bluetooth connection with am.stopBluetoothSco();
                 */
//                    unregisterReceiver(this)
                    Log.d("BLUETOOTH HOME", "Audion Connected")
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    am.mode= AudioManager.MODE_IN_COMMUNICATION
                    am.isBluetoothScoOn=true
                    am.startBluetoothSco()
                    Log.i("BLUETOOTH HOME", " Model   ${am.mode}")                }
            }
        }, IntentFilter().apply{addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)})

        Log.d("BLUETOOTH", "starting bluetooth")
        am.startBluetoothSco()


        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.INTERNET,
                ),
                1000
            )
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        registerReceiver(broadCastReceiverBluetooth, filter)

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        binding.btnRecord.setOnClickListener {
            binding.btnStop.visibility = View.VISIBLE
            binding.btnRecord.visibility = View.INVISIBLE
            startRecording()
        }
        binding.btnStop.setOnClickListener{
            binding.btnRecord.visibility = View.VISIBLE
            binding.btnStop.visibility = View.INVISIBLE
            stopRecording()
        }
        binding.btnPlay.setOnClickListener{
            startPlaying()
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun bleConnection(){
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var speakerDevice: AudioDeviceInfo? = null
        val devices: List<AudioDeviceInfo> = audioManager.availableCommunicationDevices
        audioManager.mode = AudioManager.MODE_IN_CALL
        for (device in devices) {
            Log.i("device ", device.toString())
            Log.i("device type", device.type.toString())
            if (device!!.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                speakerDevice = device
                break
            }
        }
        if (speakerDevice != null) {
            // Turn speakerphone ON.
            Log.i("speakerDevice ", speakerDevice.toString())
            val result = audioManager.setCommunicationDevice(speakerDevice)
            Log.i("result ", result.toString())
            if (!result) {
                // Handle error.
            }
            // Turn speakerphone OFF.
            //audioManager.clearCommunicationDevice()
        }
    }
    private fun startRecording() {
        Toast.makeText(applicationContext, "Recording", Toast.LENGTH_LONG).show()
        try {
            archivo = File.createTempFile("temporal", ".m4a", applicationContext.cacheDir)
        }catch (e:IOException){

            Log.e("error archive", "$e")
        }
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(archivo!!.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            try {
                prepare()
                start()
            }catch (e:IllegalStateException ) {
                Log.e("error", "prepare() failed ${e.printStackTrace()}")
            } catch (e: IOException) {
                Log.e("error", "prepare() failed")
            }
        }
    }
    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Stop", Toast.LENGTH_SHORT).show()
        }

    }
    private fun startPlaying() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Play the message", Toast.LENGTH_SHORT).show()
        }
        player = MediaPlayer()
        try {
            Log.i("path playing",archivo!!.absolutePath)
            player!!.setDataSource(archivo!!.absolutePath)
        } catch (e: IOException) {
        }
        try {
            player!!.prepare()
        } catch (e: IOException) {
        }
        player?.start()


    }
    private val broadCastReceiverBluetooth = object : BroadcastReceiver() {
        @SuppressLint("ResourceType")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            am.startBluetoothSco()
                            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                            Log.i("BLUETOOTH HOME","************ AUDIO $state *************")
                        },2000)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.i("BLUETOOTH HOME","************ DISCONECTED *************")
                    am.mode= AudioManager.MODE_NORMAL
                    Log.i("BLUETOOTH HOME", " Model   ${am.mode}")

                }
            }
        }
    }

}

