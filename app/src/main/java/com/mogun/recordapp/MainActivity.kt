package com.mogun.recordapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mogun.recordapp.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity(), OnTimerTickListener {

    companion object {
        private const val REQUEST_RECORD_AUDIO_CODE = 200
    }

    // release -> recording -> release
    // release -> play -> release
    private enum class State {
        RELEASE, RECORDING, PLAYING
    }

    private lateinit var timer: Timer

    private lateinit var binding: ActivityMainBinding
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var fileName: String = ""
    private var state: State = State.RELEASE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileName = "${externalCacheDir?.absolutePath}/audiorecordtest.3gp"
        timer = Timer(this)

        binding.recordButton.setOnClickListener {
            when (state) {
                State.RELEASE -> {
                    record()
                }
                State.RECORDING -> {
                    onRecord(false)
                }
                State.PLAYING -> {

                }
            }
        }

        binding.playButton.setOnClickListener {
            when (state) {
                State.RELEASE -> onPlay(true)
                else -> {}
            }
        }

        binding.playButton.isEnabled = false
        binding.playButton.alpha = 0.3f

        binding.stopButton.setOnClickListener {
            when (state) {
                State.PLAYING -> {
                    onPlay(false)
                }
                else -> {}
            }
        }
    }

    private fun record() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 권한이 허용된 상태
                onRecord(true)
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.RECORD_AUDIO
            ) -> {
                // 권한 요청
                showPermissionRationalDialog()
            }

            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }
        }
    }

    private fun onPlay(start: Boolean) = if (start) startPlaying() else stopPlaying()

    // 녹음 시작
    private fun onRecord(start: Boolean) = if (start) startRecording() else stopRecording()

    private fun startRecording() {
        state = State.RECORDING
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)   // 마이크를 사용하겠다
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP) // 3gp 형식으로 저장
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)   // AMR_NB 형식으로 저장

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("APP", "prepare() failed $e")
            }

            start()
        }

        binding.waveformView.clearData()
        timer.start()

        binding.recordButton.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.baseline_stop_24)
        )
        binding.recordButton.imageTintList = ColorStateList.valueOf(Color.BLACK)
        binding.playButton.isEnabled = false
        binding.playButton.alpha = 0.3f
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        // 타이머 정지
        timer.stop()

        state = State.RELEASE

        binding.recordButton.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.baseline_fiber_manual_record_24)
        )
        binding.recordButton.imageTintList = ColorStateList.valueOf(Color.RED)
        binding.playButton.isEnabled = true
        binding.playButton.alpha = 1.0f
    }

    private fun startPlaying() {
        state = State.PLAYING

        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
            } catch (e: IOException) {
                Log.e("APP", "media player prepare() fail $e")
            }

            start()
        }

        binding.waveformView.clearWave()
        timer.start()

        player?.setOnCompletionListener {
            stopPlaying()
        }

        binding.recordButton.isEnabled = false
        binding.recordButton.alpha = 0.3f
    }

    private fun stopPlaying() {
        state = State.RELEASE

        player?.release()
        player = null

        timer.stop()

        binding.recordButton.isEnabled = true
        binding.recordButton.alpha = 1.0f
    }

    private fun showPermissionRationalDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.permission_setting_message))
            .setPositiveButton("권한 허용하기") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }
            .setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun showPermissionSettingDialog() {
        AlertDialog.Builder(this)
            .setMessage("앱을 정상적으로 이용하기 위해 녹음 권한을 허용합니다. 앱 설정 화면으로 진입 후 권한을 허용해주세요.")
            .setPositiveButton("권한 변경하러가기") { _, _ ->
                navigateToAppSetting()
            }
            .setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    // 상세 설정화면으로 이동하기
    private fun navigateToAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted =
            requestCode == REQUEST_RECORD_AUDIO_CODE &&
                    grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        if (audioRecordPermissionGranted) {
            onRecord(true)
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                showPermissionRationalDialog()
            } else {
                showPermissionSettingDialog()
            }
        }
    }

    override fun onTick(duration: Long) {
        val millisecond = duration % 1000
        val second = (duration / 1000) % 60
        val minute = (duration / 1000 / 60)

        binding.timerTextView.text = String.format("%02d:%02d.%02d", minute, second, millisecond / 10)

        if (state == State.PLAYING) {
            binding.waveformView.replayAmplitude()
        } else if (state == State.RECORDING) {
            binding.waveformView.addAmplitude(recorder?.maxAmplitude?.toFloat() ?: 0f)
        }
    }
}
