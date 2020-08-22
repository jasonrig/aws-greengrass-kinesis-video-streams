package by.jasonrig.streaming

import com.amazonaws.services.lambda.runtime.Context
import org.freedesktop.gstreamer.GstObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

typealias StreamParameters = Map<String, String>

/**
 * Implements the AWS Lambda function that controls the stream
 */
class Lambda : IoTRequest(), StreamLifecycleEvents {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Lambda::class.java)

        // The current stream object and parameters
        var currentStreamer: Pair<Streamer, StreamParameters>? = null

        // The AWS credentials provider used to authenticate the stream
        val credentialsProvider: AwsCredentialsProvider = GreengrassCredentialsProvider()

        // JSON keys for parsing the incoming requests
        const val TASK_KEY = "task"
        const val START_TASK = "start"
        const val STOP_TASK = "stop"
        const val STATUS_TASK = "status"
        const val VIDEO_DEVICE_KEY = "videoDevice"
        const val STREAM_NAME_KEY = "streamName"
        const val AWS_REGION = "awsRegion"
    }

    /**
     * Parses and executes the stream control request
     * @param input the incoming stream control request
     * @param context the request context
     * @return the result of the stream control request
     */
    override fun handleIoTRequest(input: Map<String, String>?, context: Context?): Response {
        require(input != null) {
            return Response("Missing input", Response.Status.ERROR)
        }

        return when (input[TASK_KEY]) {
            START_TASK -> startTask(input)
            STOP_TASK -> stopTask()
            STATUS_TASK -> statusTask()
            else -> Response("Invalid task or task not specified", Response.Status.ERROR)
        }
    }

    /**
     * Reports the current stream status and configuration
     * @return the result of the status request
     */
    private fun statusTask(): Response {
        if (currentStreamer == null) {
            return Response("No stream running", Response.Status.NOTICE)
        }
        return Response("Stream active", Response.Status.NOTICE, extra = currentStreamer?.second)
    }

    /**
     * Starts the stream
     * The incoming request must contain:
     *  - videoDevice: the path to the video device, e.g. /dev/video0 (must be enabled in AWS Greengrass configuration)
     *  - streamName: the name of the AWS Kinesis Video Stream name (AWS Greengrass group role must have the correct permissions in AWS IAM)
     *  - awsRegion: the region of the AWS Kinesis Video stream
     * @param input the request input
     * @return the result of the stream start request
     */
    private fun startTask(input: Map<String, String>): Response {
        // If `currentStreamer` is not null, it means that the stream is currently running,
        // so stop the current stream before starting it again.
        if (currentStreamer != null) {
            publishMessage(stopTask())
        }

        // Set up the configuration parameters from the request object
        // An error will be returned if the required parameters are missing
        val config = try {
            Streamer.ConnectionParameters(
                videoDevice = input.getOrDefault(VIDEO_DEVICE_KEY, "/dev/video0"),
                streamName = input[STREAM_NAME_KEY] ?: error("Stream name [$STREAM_NAME_KEY] must be provided"),
                awsRegion = input[AWS_REGION] ?: error("AWS Region [$AWS_REGION] must be provided")
            )
        } catch (e: IllegalStateException) {
            return Response("Error: ${e.message}", Response.Status.ERROR)
        }

        // Create and start the streamer
        val newStreamer = Streamer(config, credentialsProvider, this)
        newStreamer.start()

        // Keep references to the current configuration and streamer object
        currentStreamer = Pair(newStreamer, input)

        // Return the result of the request (SUCCESS if we get this far)
        return Response("Stream started", Response.Status.SUCCESS)
    }

    /**
     * Stops any running stream
     */
    private fun stopTask(): Response {
        currentStreamer?.first?.stop()
        currentStreamer = null
        return Response("Streaming is stopped", Response.Status.SUCCESS)
    }

    /**
     * Lifecycle event - executed when the stream ends
     * @see StreamLifecycleEvents.onEnd
     * @see org.freedesktop.gstreamer.Bus.EOS
     * @param source the gstreamer handling the stream
     */
    override fun onEnd(source: GstObject) {
        val msg = "[${source.name}] gstreamer stopped"
        logger.info(msg)
        publishMessage(Response(msg, Response.Status.SUCCESS))
    }

    /**
     * Lifecycle event - executed when a stream error is encountered
     * @see StreamLifecycleEvents.onError
     * @see org.freedesktop.gstreamer.Bus.ERROR
     * @param source the gstreamer handling the stream
     * @param code error code
     * @param message error message
     */
    override fun onError(source: GstObject, code: Int, message: String) {
        val msg = "[${source.name}] $message"
        logger.error(msg)
        publishMessage(Response(msg, Response.Status.ERROR, code))
        currentStreamer?.also {
            publishMessage(Response("Restarting stream", Response.Status.NOTICE))
            startTask(it.second)
        }
    }

    /**
     * Lifecycle event - executed when a warning is emitted by the stream
     * @see StreamLifecycleEvents.onWarn
     * @see org.freedesktop.gstreamer.Bus.WARNING
     * @param source the gstreamer handling the stream
     * @param code warning code
     * @param message warning message
     */
    override fun onWarn(source: GstObject, code: Int, message: String) {
        val msg = "[${source.name}] $message"
        logger.warn(msg)
        publishMessage(Response(msg, Response.Status.WARNING, code))
    }

    /**
     * Lifecycle event - executed when the credentials used to authenticate with the AWS Kinesis Video Streams endpoint
     * expire
     * @see StreamLifecycleEvents.onCredentialsExpiration
     */
    override fun onCredentialsExpiration() {
        currentStreamer?.also {
            logger.info("Stream is restarting, credentials have expired")
            publishMessage(Response("Restarting stream", Response.Status.NOTICE))
            startTask(it.second)
        }
    }

}