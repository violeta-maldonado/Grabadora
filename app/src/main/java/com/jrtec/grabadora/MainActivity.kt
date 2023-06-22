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
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    var formattedSpeech: StringBuffer = StringBuffer()
    var recorder: MediaRecorder? = null
    var player: MediaPlayer? = null
    private lateinit var recognizer: SpeechRecognizer
    private val archivo = Environment.getExternalStorageDirectory().absolutePath + "/audio.3gp"

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
                    unregisterReceiver(this)
                }
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
            startListening()
        }
        binding.btnStop.setOnClickListener{
            binding.btnRecord.visibility = View.VISIBLE
            binding.btnStop.visibility = View.INVISIBLE
            stopRecording()
        }
        binding.btnPlay.setOnClickListener{
            startPlaying()
        }
        setupMediaRecorder()
    }

    private fun setupMediaRecorder() {
        recorder = MediaRecorder()
        recorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        recorder?.setOutputFile(getAudioFilePath())
    }
    private fun getAudioFilePath(): String {
        return "${externalCacheDir?.absolutePath}/audio_recording.3gp"
    }

    private fun stopRecording() {
        binding.btnRecord.visibility = View.VISIBLE
        binding.btnStop.visibility = View.INVISIBLE
        recognizer.stopListening()
        recognizer.destroy()
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            player!!.setDataSource(getAudioFilePath())
            player!!.prepare()
            player!!.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_CALLING_PACKAGE,
            "com.domain.app"
        )

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val mediaRecorderListener: MediaRecorder.OnErrorListener =
            MediaRecorder.OnErrorListener { _, _, _ ->
                // Handle media recorder errors if necessary
            }
        var listener: RecognitionListener = object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val voiceResults = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (voiceResults == null) {
                    println("No voice results")
                } else {
                    for (match in voiceResults) {
                        formattedSpeech.append(String.format("\n- %s", match.toString()))
                        binding.textView.text = formattedSpeech.toString()
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle) {
                //recorder?.setOutputFile(getAudioFilePath())
                try {
                    recorder!!.prepare()
                    recorder!!.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                println("Ready for speech")
            }

            /**
             * ERROR_NETWORK_TIMEOUT = 1;
             * ERROR_NETWORK = 2;
             * ERROR_AUDIO = 3;
             * ERROR_SERVER = 4;
             * ERROR_CLIENT = 5;
             * ERROR_SPEECH_TIMEOUT = 6;
             * ERROR_NO_MATCH = 7;
             * ERROR_RECOGNIZER_BUSY = 8;
             * ERROR_INSUFFICIENT_PERMISSIONS = 9;
             *
             * @param error code is defined in SpeechRecognizer
             */
            override fun onError(error: Int) {
                System.err.println("Error listening for speech: $error")
                binding.btnRecord.visibility = View.VISIBLE
                binding.btnStop.visibility = View.INVISIBLE
                recognizer.stopListening()
                recognizer.destroy()
                try {
                    recorder!!.stop()
                    recorder!!.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onBeginningOfSpeech() {
                System.err.println("Error listening for speech: onBeginning")
                // TODO Auto-generated method stub
            }

            override fun onBufferReceived(buffer: ByteArray) {
                System.err.println("Error listening for speech buffer: $buffer")
                // TODO Auto-generated method stub
            }

            override fun onEndOfSpeech() {
                System.err.println("Error listening for speech: oonEndOfSpeech")
                binding.btnRecord.visibility = View.VISIBLE
                binding.btnStop.visibility = View.INVISIBLE
                recognizer.stopListening()
                recognizer.destroy()
                try {
                    recorder!!.stop()
                    recorder!!.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // TODO Auto-generated method stub
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                // TODO Auto-generated method stub
            }

            override fun onPartialResults(partialResults: Bundle) {
                System.err.println("Error listening for speech: onPartialResult")
                // TODO Auto-generated method stub
            }

            override fun onRmsChanged(rmsdB: Float) {
                System.err.println("Error listening for speech rmsdB: $rmsdB")
                // TODO Auto-generated method stub
            }
        }
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }
}

