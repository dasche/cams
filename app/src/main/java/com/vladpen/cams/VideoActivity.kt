package com.vladpen.cams

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import com.vladpen.FileData
import com.vladpen.StreamData
import com.vladpen.StreamDataModel
import com.vladpen.VideoGestureDetector
import com.vladpen.cams.databinding.ActivityVideoBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.IOException

class VideoActivity : AppCompatActivity(), MediaPlayer.EventListener {
    private val binding by lazy { ActivityVideoBinding.inflate(layoutInflater) }

    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var stream: StreamDataModel

    private lateinit var gestureDetector: VideoGestureDetector

    private var streamId: Int = -1 // -1 means "no stream"
    private var remotePath: String = "" // relative SFTP path
    private val seekStep: Long = 10000 // milliseconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initActivity()
    }

    private fun initActivity() {
        streamId = intent.getIntExtra("streamId", -1)
        remotePath = intent.getStringExtra("remotePath") ?: ""

        stream = StreamData.getById(streamId) ?: return

        videoLayout = binding.videoLayout

        libVlc = LibVLC(this, ArrayList<String>().apply {
            if (stream.tcp && remotePath == "")
                add("--rtsp-tcp")
        })
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.setEventListener(this)

        initToolbar()
        if (remotePath != "")
            initVideoBar()

        gestureDetector = VideoGestureDetector(this, videoLayout)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initToolbar() {
        binding.toolbar.tvToolbarLabel.text = stream.name

        binding.toolbar.btnBack.setOnClickListener {
            if (remotePath == "") {
                val mainIntent = Intent(this, MainActivity::class.java)
                startActivity(mainIntent)
            } else {
                val filesIntent = Intent(this, FilesActivity::class.java)
                    .putExtra("streamId", streamId)
                    .putExtra("remotePath", FileData.getParentPath(remotePath))
                startActivity(filesIntent)
            }
        }

        if (stream.sftp == null)
            return

        if (remotePath == "") {
            binding.toolbar.tvToolbarLink.text = getString(R.string.files)
            binding.toolbar.tvToolbarLink.setTextColor(getColor(R.color.files_link))
        } else {
            binding.toolbar.tvToolbarLink.text = getString(R.string.live)
            binding.toolbar.tvToolbarLink.setTextColor(getColor(R.color.live_link))
        }
        binding.toolbar.tvToolbarLink.setOnClickListener {
            if (remotePath == "") {
                val filesIntent = Intent(this, FilesActivity::class.java)
                    .putExtra("streamId", streamId)
                startActivity(filesIntent)
            } else {
                val videoIntent = Intent(this, VideoActivity::class.java)
                    .putExtra("streamId", streamId)
                startActivity(videoIntent)
            }
        }
        binding.toolbar.tvToolbarLink.visibility = View.VISIBLE
    }

    private fun initVideoBar() {
        binding.videoBar.btnPlay.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                it.setBackgroundResource(R.drawable.ic_baseline_play_arrow_24)
            } else {
                mediaPlayer.play()
                it.setBackgroundResource(R.drawable.ic_baseline_pause_24)
            }
        }
        binding.videoBar.btnPrevFile.setOnClickListener {
            next(false)
        }
        binding.videoBar.btnSeekBack.setOnClickListener {
            dropRate() // prevent lost keyframe
            // we can't use the "position" here (file size changes during playback)
            mediaPlayer.time -= seekStep
        }
        binding.videoBar.btnNextFile.setOnClickListener {
            next()
        }
        binding.videoBar.tvSpeed.setOnClickListener {
            if (mediaPlayer.rate < 2f) {
                mediaPlayer.rate = 4f
                binding.videoBar.tvSpeed.setText(R.string.speed_fast)
            } else {
                dropRate()
            }
        }
        binding.videoBar.root.visibility = View.VISIBLE
    }

    private fun dropRate() {
        mediaPlayer.rate = 1f
        binding.videoBar.tvSpeed.setText(R.string.speed_normal)
    }

    private fun play() {
        try {
            val media =
                if (remotePath == "")
                    Media(libVlc, Uri.parse(stream.url))
                else
                    Media(libVlc, FileData.getTmpFile(this, remotePath).absolutePath)

            media.apply {
                setHWDecoderEnabled(false, false)
                if (remotePath == "")
                    addOption(":network-caching=150")
                else {
                    addOption(":avcodec-skip-frame=-1")
                }
                mediaPlayer.media = this
            }.release()

            mediaPlayer.play()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun next(fwd: Boolean = true) {
        remotePath = FileData(this, stream.sftp).getNext(remotePath, fwd)
        if (remotePath != "") {
            binding.videoBar.btnPlay.setBackgroundResource(R.drawable.ic_baseline_pause_24)
            play()
        } else {
            finish()
            intent.putExtra("streamId", streamId).putExtra("remotePath", "")
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        mediaPlayer.attachViews(videoLayout, null, false, false)
        play()
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        libVlc.release()
    }

    override fun onEvent(ev: MediaPlayer.Event) {
        if (ev.type == MediaPlayer.Event.Buffering && ev.buffering == 100f) {
            binding.pbLoading.visibility = View.GONE
        } else if (ev.type == MediaPlayer.Event.EndReached && remotePath != "") {
            // Event.EndReached may raises before the file loading will ends,
            // so don't seek forward
            next()
        }
    }

    override fun onTouchEvent(event: MotionEvent) =
        gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        gestureDetector.reset()
    }
}