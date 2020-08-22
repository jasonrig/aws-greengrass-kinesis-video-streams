package by.jasonrig.streaming

import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Bus.ERROR
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.lowlevel.MainLoop
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


/**
 * Manages a video stream to AWS Kiesis Video Streams
 * @param params the connection parameters
 * @param credentialsProvider a credentials prover that resolved credentials sufficient to upload a video stream
 * @param callback callbacks for stream events, such as errors and warnings
 */
class Streamer(
    params: ConnectionParameters,
    credentialsProvider: AwsCredentialsProvider,
    private val callback: StreamLifecycleEvents?
) {

    companion object {
        // Environment variable names to get the path to gstreamer native libraries
        // and glib
        private const val GSTREAMER_ENV = "GSTREAMER_PATH"
        private const val GLIB_ENV = "GLIB_PATH"

        init {
            // Fetch the paths to native libraries and update the system property
            val paths = ArrayList<String>(3)
            if (System.getenv(GSTREAMER_ENV) != null) {
                paths.add(System.getenv(GSTREAMER_ENV))
            }
            if (System.getenv(GLIB_ENV) != null) {
                paths.add(System.getenv(GLIB_ENV))
            }
            val props = System.getProperties()
            if (props.getProperty("jna.library.path") != null) {
                paths.add(props.getProperty("jna.library.path"))
            }
            if (paths.size > 0) {
                props.setProperty("jna.library.path", paths.joinToString(":"))
            }
            System.setProperties(props)

            // Initialise the gstreamer object
            Gst.init()
        }
    }

    // Thread that will handle the stream
    private var worker: Thread? = null
    private var loop = MainLoop()

    // Gstreamer pipe
    private val pipe: Pipeline

    // Timer that tracks credentials expiration
    private val credentialsTimer = Timer()

    init {
        // Prepare the gstreamer credential configuration
        val credentials = credentialsProvider.resolveCredentials()
        val credentialsParameters = try {

            // If we have a `GreengrassCredentials` object, create a credentials file that gstreamer can consume
            // and set up a trigger for the expiration time
            val ggCredentials = credentials as GreengrassCredentials

            // Trigger the credentials expiration callback at the expiration time
            credentialsTimer.schedule(object : TimerTask() {
                override fun run() {
                    callback?.onCredentialsExpiration()
                }
            }, Date.from(ggCredentials.expiry.minusSeconds(30)))

            val expiration = DateTimeFormatter.ISO_INSTANT.format(ggCredentials.expiry)
            val credentialsFile = File.createTempFile("aws_credentials", "")
            credentialsFile.writeText("CREDENTIALS ${ggCredentials.accessKeyId()} $expiration ${ggCredentials.secretAccessKey()} ${ggCredentials.sessionToken()}")
            "credential-path=\"${credentialsFile.absolutePath}\""
        } catch (e: ClassCastException) {
            // If we fail to cast to GreengrassCredentials, then just use the Access Key and Secret Access Key directly
            "access-key=\"${credentials.accessKeyId()}\" secret-key=\"${credentials.secretAccessKey()}\""
        }

        // Prepare the full gstreamer configuration
        val ksvConfig =
            "kvssink stream-name=\"${params.streamName}\" storage-size=512 aws-region=\"${params.awsRegion}\" $credentialsParameters "
        val x264encConfig = "x264enc  bframes=0 key-int-max=${params.keyIntMax} bitrate=${params.bitRate}"
        val binDescription = if (params.videoDevice == null) {
            "avfvideosrc ! videoconvert ! capsfilter caps=video/x-raw,width=${params.frameSizeWidth},height=${params.frameSizeHeight} ! $x264encConfig ! video/x-h264,stream-format=avc,alignment=au,profile=baseline ! $ksvConfig"
        } else {
            "v4l2src do-timestamp=TRUE device=${params.videoDevice} ! videoconvert ! video/x-raw,format=I420,width=${params.frameSizeWidth},height=${params.frameSizeHeight},framerate=${params.frameRate}/1 ! $x264encConfig ! video/x-h264,stream-format=avc,alignment=au,profile=baseline ! $ksvConfig"
        }

        // Parse the configuration
        val bin = Gst.parseBinFromDescription(binDescription, true)
        pipe = Pipeline()
        pipe.add(bin)

        // Connect to EOS and ERROR on the bus for cleanup and error messages.
        val bus = pipe.bus
        bus.connect(Bus.EOS {
            callback?.onEnd(it)
            stop()
        })
        bus.connect(ERROR { source, code, message ->
            callback?.onError(source, code, message)
            stop()
        })
        bus.connect(Bus.WARNING { source, code, message ->
            callback?.onWarn(source, code, message)
        })
    }

    /**
     * Start the stream
     */
    fun start() {
        if (!loop.isRunning) {
            val worker = Thread {
                pipe.play()
                loop.run()
            }
            worker.start()
            this.worker = worker
        }
    }

    /**
     * Stop the stream
     */
    fun stop() {
        pipe.stop()
        loop.quit()
        worker?.join()
    }

    /**
     * Conection parameters
     * @param videoDevice the path to the video device
     * @param streamName the name of the AWS Kinesis Video Streams stream
     * @param awsRegion the region in which the stream is located
     * @param frameSizeHeight height of the video frame
     * @param frameSizeWidth width of the video frame
     * @param frameRate the frame rate of the video stream
     * @param bitRate the bit rate of the video stream
     * @param keyIntMax maximal distance between two key-frames (0 for automatic)
     */
    data class ConnectionParameters(
        val videoDevice: String? = null,
        val streamName: String,
        val awsRegion: String,
        val frameSizeHeight: Int = 480,
        val frameSizeWidth: Int = 640,
        val frameRate: Int = 30,
        val bitRate: Int = 500,  // https://gstreamer.freedesktop.org/documentation/x264/index.html?gi-language=c#x264enc:bitrate
        val keyIntMax: Int = 45  // https://gstreamer.freedesktop.org/documentation/x264/index.html?gi-language=c#x264enc:key-int-max
    )
}